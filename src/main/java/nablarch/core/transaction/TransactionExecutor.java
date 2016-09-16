package nablarch.core.transaction;

import nablarch.core.log.Logger;
import nablarch.core.log.LoggerManager;
import nablarch.core.util.annotation.Published;

/**
 * トランザクション制御ロジックを持つ抽象クラス。
 * <p/>
 * 本クラスを使用することにより、簡単にトランザクション制御ロジックを実装することが出来る。
 * また、エラー処理時に再度エラーが発生した場合のログ出力の責務を本クラスが持つため、
 * 本クラスを実装したクラスではトランザクション制御以外の部分に着目する必要がなくなる。
 *
 * @param <T> トランザクション処理からの戻り型
 * @author hisaaki sioiri
 */
@Published(tag = "architect")
public abstract class TransactionExecutor<T> {

    /** Logger */
    private static final Logger LOGGER = LoggerManager.get(
            TransactionExecutor.class);

    /** トランザクション */
    private final Transaction transaction;

    /**
     * トランザクション制御オブジェクトを生成する。
     *
     * @param transaction トランザクション
     */
    public TransactionExecutor(Transaction transaction) {
        this.transaction = transaction;
    }

    /**
     * トランザクションを実行する。
     * <p/>
     * {@link #doInTransaction(Transaction)}を呼び出しトランザクション制御を行う。
     *
     * @return 処理結果
     */
    public final T execute() {
        try {
            transaction.begin();
            T result = doInTransaction(transaction);
            transaction.commit();
            return result;
        } catch (RuntimeException e) {
            doErrorTransaction(transaction, e);
            throw e;
        } catch (Error e) {
            doErrorTransaction(transaction, e);
            throw e;
        }
    }

    /**
     * トランザクション実行時に例外が発生した場合の処理。
     * <p/>
     * {@link #onError(Transaction, Throwable)}を呼び出し、
     * エラー時の処理で再度例外が発生した場合にはワーニングログに例外情報を出力する。
     *
     * @param transaction トランザクション
     * @param throwable 発生した例外
     */
    private void doErrorTransaction(Transaction transaction,
            Throwable throwable) {
        try {
            onError(transaction, throwable);
        } catch (RuntimeException e) {
            writeWarnLog(throwable);
            throw e;
        } catch (Error e) {
            writeWarnLog(throwable);
            throw e;
        }
    }

    /**
     * エラー時の処理を行う。
     * <p/>
     * 本メソッドではトランザクションのロールバックのみを行う。
     * ロールバック以外の処理を必要とする場合には、本メソッドをオーバライドすること。
     *
     * @param transaction トランザクション
     * @param throwable 発生した例外
     */
    protected void onError(Transaction transaction,
            Throwable throwable) {
        transaction.rollback();
    }

    /**
     * トランザクション内で実行する処理を実装する。
     * <p/>
     * 必要がある場合は、本メソッド内でトランザクション制御を行っても良い。
     *
     * @param transaction トランザクションオブジェクト
     * @return 処理結果
     */
    protected abstract T doInTransaction(Transaction transaction);

    /**
     * ワーニングレベルのログを出力する。
     *
     * @param throwable ログに出力する例外
     */
    private static void writeWarnLog(Throwable throwable) {
        LOGGER.logWarn("transaction has failed.", throwable);
    }
}
