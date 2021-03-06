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
package io.nuls.consensus.poc.block.validator;

import io.nuls.core.validate.NulsDataValidator;
import io.nuls.core.validate.ValidateResult;
import io.nuls.protocol.model.Block;
import io.nuls.protocol.model.NulsDigestData;

import java.util.List;

/**
 * @author Niels
 * @date 2017/11/17
 */
public class BlockMerkleValidator implements NulsDataValidator<Block> {
    private static final String ERROR_MESSAGE = "Merkle Hash is wrong!";
    public static final BlockMerkleValidator INSTANCE = new BlockMerkleValidator();

    private BlockMerkleValidator() {
    }

    public static BlockMerkleValidator getInstance() {
        return INSTANCE;
    }

    @Override
    public ValidateResult validate(Block data) {
        ValidateResult result = ValidateResult.getFailedResult(ERROR_MESSAGE);
        do {
            if (null == data) {
                result.setMessage("Data is null!");
                break;
            }
            if (data.getHeader().getMerkleHash().equals(buildMerkleHash(data))) {
                result = ValidateResult.getSuccessResult();
                break;
            }
        } while (false);
        return result;
    }

    private NulsDigestData buildMerkleHash(Block data) {
        List<NulsDigestData> txHashList = data.getTxHashList();
        return NulsDigestData.calcMerkleDigestData(txHashList);
    }
}
