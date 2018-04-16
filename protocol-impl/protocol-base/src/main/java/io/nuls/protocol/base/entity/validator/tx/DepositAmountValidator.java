/*
 * MIT License
 *
 * Copyright (c) 2017-2018 nuls.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.nuls.protocol.base.entity.validator.tx;

import io.nuls.protocol.base.entity.tx.PocJoinConsensusTransaction;
import io.nuls.protocol.base.cache.manager.member.ConsensusCacheManager;
import io.nuls.protocol.base.constant.PocConsensusConstant;
import io.nuls.protocol.entity.Consensus;
import io.nuls.protocol.base.entity.member.Deposit;
import io.nuls.core.chain.entity.Na;
import io.nuls.core.constant.ErrorCode;
import io.nuls.core.constant.SeverityLevelEnum;
import io.nuls.core.context.NulsContext;
import io.nuls.core.exception.NulsException;
import io.nuls.core.validate.NulsDataValidator;
import io.nuls.core.validate.ValidateResult;

import java.util.List;

/**
 * @author Niels
 * @date 2018/1/17
 */
public class DepositAmountValidator implements NulsDataValidator<PocJoinConsensusTransaction> {

    private static final DepositAmountValidator INSTANCE = new DepositAmountValidator();
    private ConsensusCacheManager consensusCacheManager = ConsensusCacheManager.getInstance();

    private DepositAmountValidator() {
    }

    public static DepositAmountValidator getInstance() {
        return INSTANCE;
    }

    @Override
    public ValidateResult validate(PocJoinConsensusTransaction data) {
        Na limit = PocConsensusConstant.ENTRUSTER_DEPOSIT_LOWER_LIMIT;
        Na max = PocConsensusConstant.SUM_OF_DEPOSIT_OF_AGENT_UPPER_LIMIT;
        //+2原因：验证的交易可能属于a高度，从cache中获取它之前的抵押时，只能获取某个高度之前的，所以是当前最新高度a-1之后的第二个高度
        List<Consensus<Deposit>> list = consensusCacheManager.getDepositListByAgentId(data.getTxData().getExtend().getAgentHash(), NulsContext.getInstance().getBestHeight()+2);
        if (list == null) {
            return ValidateResult.getSuccessResult();
        }
        Na total = Na.ZERO;
        for (Consensus<Deposit> cd : list) {
            total = total.add(cd.getExtend().getDeposit());
        }
        if (limit.isGreaterThan(data.getTxData().getExtend().getDeposit())) {
            return ValidateResult.getFailedResult(ErrorCode.DEPOSIT_NOT_ENOUGH);
        }
        if (max.isLessThan(total)) {
            return ValidateResult.getFailedResult(ErrorCode.DEPOSIT_TOO_MUCH);
        }

        try {
            if (!data.getTxData().getExtend().getDeposit().equals(data.getCoinData().getTotalNa())) {
                return ValidateResult.getFailedResult(SeverityLevelEnum.FLAGRANT_FOUL, ErrorCode.DEPOSIT_ERROR);
            }
        } catch (NulsException e) {
            return ValidateResult.getFailedResult(ErrorCode.ORPHAN_TX);
        }
        return ValidateResult.getSuccessResult();
    }

}