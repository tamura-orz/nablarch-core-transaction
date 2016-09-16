package nablarch.core.transaction;

import java.sql.SQLException;

import nablarch.core.util.annotation.Published;
import nablarch.fw.handler.retry.Retryable;

/**
 * トランザクションタイムアウトエラー。
 *
 * @author hisaaki sioiri
 */
@Published(tag = "architect")
public class TransactionTimeoutException extends RuntimeException implements Retryable {

    /** 例外のデフォルトメッセージ */
    private static final String MESSAGE_TEMPLATE = "transaction was timeout. transaction execution time = [%d]";

    /**
     * コンストラクタ。
     * <p/>
     * デフォルトメッセージを持つトランザクションタイムアウト例外を生成する。
     *
     * @param transactionExecutionTime トランザクションの実行時間
     */
    public TransactionTimeoutException(long transactionExecutionTime) {
        this(transactionExecutionTime, null);
    }

    /**
     * コンストラクタ。
     * <p/>
     * デフォルトメッセージを持つトランザクションタイムアウト例外を生成する。
     *
     * @param transactionExecutionTime トランザクションの実行時間
     * @param e 発生したSQL文実行時例外
     */
    public TransactionTimeoutException(long transactionExecutionTime, SQLException e) {
        super(String.format(MESSAGE_TEMPLATE, transactionExecutionTime), e);
    }
}

