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
package io.nuls.consensus.poc.block.validator.header;

import io.nuls.account.service.intf.AccountService;
import io.nuls.core.validate.NulsDataValidator;
import io.nuls.core.validate.ValidateResult;
import io.nuls.protocol.context.NulsContext;
import io.nuls.protocol.model.BlockHeader;

/**
 * @author Niels
 * @date 2017/11/17
 */
public class HeaderSignValidator implements NulsDataValidator<BlockHeader> {
    private static final String ERROR_MESSAGE = "block header sign check failed";
    public static final HeaderSignValidator INSTANCE = new HeaderSignValidator();
    private AccountService accountService = NulsContext.getServiceBean(AccountService.class);
    private HeaderSignValidator(){}
    public static HeaderSignValidator getInstance(){
        return INSTANCE;
    }
    @Override
    public ValidateResult validate(BlockHeader data) {
        if(data.getScriptSig()==null){
            return ValidateResult.getFailedResult(ERROR_MESSAGE);
        }
        ValidateResult verifyRsult = data.getScriptSig().verifySign(data.getHash());
        return verifyRsult;
    }
}
