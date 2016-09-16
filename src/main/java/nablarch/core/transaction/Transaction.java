package nablarch.core.transaction;

import nablarch.core.util.annotation.Published;

/**
 * トランザクション制御を行うインタフェース。
 *
 * @author Hisaaki Sioiri
 */
@Published(tag = "architect")
public interface Transaction {

    /** トランザクションを開始する。 */
    void begin();

    /** 現在のトランザクションをコミットする。 */
    void commit();

    /** 現在のトランザクションをロールバックする。 */
    void rollback();

}

