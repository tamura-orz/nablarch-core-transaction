package nablarch.core.transaction;

/**
 * トランザクション制御オブジェクト({@link Transaction})を生成するインタフェース。。
 *
 * @author Hisaaki Sioiri
 */
public interface TransactionFactory {

    /**
     * トランザクションオブジェクトを生成する。
     *
     * @param resourceName リソース名
     * @return トランザクションオブジェクト
     */
    Transaction getTransaction(String resourceName);

}
