import java.util.ArrayList;

public class TxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     * values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        double totalInput = 0, totalOutput = 0;
        UTXOPool tempPool = new UTXOPool();

        for (int i = 0; i < tx.getInputs().size(); i++) {
            Transaction.Input currentInput = tx.getInput(i);

            UTXO currentUtxo = new UTXO(currentInput.prevTxHash, currentInput.outputIndex);
            Transaction.Output currentOutput = this.utxoPool.getTxOutput(currentUtxo);

            // checks (1)
            if (!this.utxoPool.contains(currentUtxo))
                return false;

            // checks (2)
            if (!Crypto.verifySignature(currentOutput.address, tx.getRawDataToSign(i), currentInput.signature))
                return false;

            // checks (3)
            if (tempPool.contains(currentUtxo))
                return false;
            tempPool.addUTXO(currentUtxo, currentOutput);

            totalInput += currentOutput.value;
        }

        // checks (4)
        for (Transaction.Output currentOutput : tx.getOutputs()) {
            if (currentOutput.value < 0)
                return false;

            totalOutput += currentOutput.value;
        }

        // checks (5)
        if (totalOutput > totalInput)
            return false;

        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> acceptedTransactions = new ArrayList<Transaction>();

        for (int i = 0; i < possibleTxs.length; i++) {
            Transaction currentTx = possibleTxs[i];

            if (this.isValidTx(currentTx)) {
                acceptedTransactions.add(currentTx);

                // adds UTXOs related to the accepted transaction output
                for (int k = 0; k < currentTx.getOutputs().size(); k++) {
                    UTXO newUtxo = new UTXO(currentTx.getHash(), k);
                    this.utxoPool.addUTXO(newUtxo, currentTx.getOutput(k));
                }

                // removes the UTXOs related to the accepted transaction input
                for (Transaction.Input currentInput : currentTx.getInputs()) {
                    UTXO confirmedUtxo = new UTXO(currentInput.prevTxHash, currentInput.outputIndex);
                    this.utxoPool.removeUTXO(confirmedUtxo);
                }
            }
        }

        return acceptedTransactions.toArray(new Transaction[acceptedTransactions.size()]);
    }
}
