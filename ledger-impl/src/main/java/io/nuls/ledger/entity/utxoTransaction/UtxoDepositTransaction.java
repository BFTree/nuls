package io.nuls.ledger.entity.utxoTransaction;

import io.nuls.core.constant.TransactionConstant;
import io.nuls.core.utils.io.NulsByteBuffer;
import io.nuls.ledger.entity.LockCoinTransaction;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by facjas on 2017/11/17.
 */
public class UtxoDepositTransaction extends LockCoinTransaction {
    public UtxoDepositTransaction() {
        this.setCanBeUnlocked(true);
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public void serializeToStream(OutputStream stream) throws IOException {

    }

    @Override
    public void parse(NulsByteBuffer byteBuffer) {

    }
}