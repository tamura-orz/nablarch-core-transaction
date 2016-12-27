package nablarch.core.transaction;

import java.util.HashMap;
import java.util.Map;

import nablarch.core.util.annotation.Published;

/**
 * スレッドに紐付けられた{@link Transaction}を保持するクラス。
 *
 * @author Koichi Asano
 */
public final class TransactionContext {

    /** デフォルトのトランザクション名 */
    public static final String DEFAULT_TRANSACTION_CONTEXT_KEY = "transaction";

    /** 隠蔽コンストラクタ。 */
    private TransactionContext() {
    }

    /** スレッドに紐付けられたトランザクション。 */
    private static final ThreadLocal<Map<String, Transaction>> transaction = new ThreadLocal<Map<String, Transaction>>() {
        @Override
        protected Map<String, Transaction> initialValue() {
            return new HashMap<String, Transaction>();
        }
    };

    /**
     * トランザクションオブジェクトを設定する。
     *
     * @param transactionName トランザクション名
     * @param tran トランザクションオブジェクト
     * @throws IllegalArgumentException 引数で渡したトランザクション名が既にスレッドローカルに登録されている場合
     */
    @Published(tag = "architect")
    public static void setTransaction(String transactionName, Transaction tran) {
        Map<String, Transaction> localMap = transaction.get();

        if (localMap.containsKey(transactionName)) {
            throw new IllegalArgumentException(String.format(
                    "specified transaction name was duplication in thread local. transaction name = [%s]",
                    transactionName));
        }
        localMap.put(transactionName, tran);
    }

    /**
     * トランザクションオブジェクトを取得する。
     * <p/>
     * トランザクション名{@value DEFAULT_TRANSACTION_CONTEXT_KEY}でスレッドローカルに登録されたトランザクションを取得する。
     *
     * @return トランザクションオブジェクト
     * @throws IllegalArgumentException トランザクション名{@value DEFAULT_TRANSACTION_CONTEXT_KEY}がスレッドローカルに登録されていない場合
     */
    @Published(tag = "architect")
    public static Transaction getTransaction() {
        return getTransaction(DEFAULT_TRANSACTION_CONTEXT_KEY);
    }

    /**
     * トランザクションオブジェクトを取得する。
     *
     * @param transactionName トランザクション名
     * @return トランザクションオブジェクト
     * @throws IllegalArgumentException 引数で指定したトランザクション名がスレッドローカルに登録されていない場合
     */
    @Published(tag = "architect")
    public static Transaction getTransaction(String transactionName) {
        Map<String, Transaction> localMap = transaction.get();
        Transaction tran = localMap.get(transactionName);
        if (tran == null) {
            throw new IllegalArgumentException(String
                    .format("specified transaction name is not register in thread local. transaction name = [%s]",
                            transactionName));
        }
        return tran;
    }

    /**
     * スレッドローカルからトランザクションを削除する。
     * <p/>
     * トランザクション名{@value DEFAULT_TRANSACTION_CONTEXT_KEY}でスレッドローカルに登録されたトランザクションを削除する。
     *
     */
    @Published(tag = "architect")
    public static void removeTransaction() {
        removeTransaction(DEFAULT_TRANSACTION_CONTEXT_KEY);
    }

    /**
     * スレッドローカルからトランザクションを削除する。
     *
     * @param transactionName トランザクション名
     */
    @Published(tag = "architect")
    public static void removeTransaction(String transactionName) {
        Map<String, Transaction> localMap = transaction.get();
        localMap.remove(transactionName);
        if (localMap.isEmpty()) {
            transaction.remove();
        }
    }

    /**
     * トランザクションが保持されているか否か。
     *
     * @param transactionName トランザクション名
     * @return トランザクションが保持されている場合は{@code true}
     */
    public static boolean containTransaction(final String transactionName) {
        return transaction.get().containsKey(transactionName);
    }
}
