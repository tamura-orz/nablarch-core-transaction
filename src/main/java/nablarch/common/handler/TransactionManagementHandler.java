package nablarch.common.handler;

import java.util.ArrayList;
import java.util.List;

import nablarch.core.transaction.Transaction;
import nablarch.core.transaction.TransactionContext;
import nablarch.core.transaction.TransactionExecutor;
import nablarch.core.transaction.TransactionFactory;
import nablarch.core.util.ObjectUtil;
import nablarch.core.util.StringUtil;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.InboundHandleable;
import nablarch.fw.OutboundHandleable;
import nablarch.fw.Result;
import nablarch.fw.TransactionEventCallback;

/**
 * 後続処理における透過的トランザクションを実現するハンドラ。<br/>
 * 通常の {@ref Handler} として使用する場合と、{@ref InboundHandleable} {@ref OutboundHandleable} として使用する場合で動作が異なる。
 * 
 * <pre>
 * {@ref Handler} として使用する場合、本ハンドラの詳細な処理内容は以下の通り。
 *
 * 1.  トランザクションファクトリから使用するトランザクションオブジェクトを取得し、
 *     {@link nablarch.core.transaction.TransactionContext}上に設定する。
 * 2.  トランザクションを開始する。
 * 3.  ハンドラスタックから次のリクエストハンドラを取得し、処理を委譲する。
 * 4a. 委譲先の処理において例外が発生しなければトランザクションをコミットする。
 * 4b. 例外が発生した場合はトランザクションをロールバックする。
 *     ただし、このとき送出された例外が、{@link #setTransactionCommitExceptions(java.util.List)}で設定された例外の
 *     いずれかのサブクラスである場合はトランザクションをコミットする。
 * 5.  トランザクションオブジェクトを{@link TransactionContext}から除去する。
 *
 * 設定例:<br/>
 * {@code
 * <component class="nablarch.common.handler.TransactionManagementHandler">
 *      <!-- トランザクションファクトリ -->
 *      <property name="transactionFactory"
 *                value="transactionFactory"/>
 *      <!-- トランザクションをコミットする例外 -->
 *      <property name="transactionCommitExceptions">
 *          <list>
 *              <value>example.TransactionCommitException</value>
 *              <value>example.TransactionCommitException2</value>
 *          </list>
 *      </property>
 * </component>
 * }
 * </pre>
 * 
 * {@ref InboundHandleable} {@ref OutboundHandleable} として使用する場合、Inbound処理の際にトランザクションを開始し、
 * Outbound処理の際にトランザクションをコミットまたはロールバックする。<br/>
 * コミットとロールバックの判定は、 isProcessSucceded が true を返すか false を返すかで判定する。
 *
 * @author Iwauo Tajima <iwauo@tis.co.jp>
 * @author Koichi Asano <asano.koichi@tis.co.jp>
 */
public class TransactionManagementHandler
extends TransactionEventCallback.Provider<Object>
implements Handler<Object, Object>, InboundHandleable, OutboundHandleable {
    /**
     * トランザクションオブジェクトを取得するためのファクトリを設定する。
     *
     * @param transactionFactory トランザクションオブジェクトを取得するためのファクトリ
     */
    public void setTransactionFactory(TransactionFactory transactionFactory) {
        assert transactionFactory != null;
        this.transactionFactory = transactionFactory;
    }

    /** トランザクションオブジェクトを取得するためのファクトリ */
    private TransactionFactory transactionFactory;

    /**
     * このハンドラが管理するトランザクションの、スレッドコンテキスト上での登録名を設定する。
     * <pre>
     * デフォルトでは既定のトランザクション名
     * ({@link TransactionContext#DEFAULT_TRANSACTION_CONTEXT_KEY})を使用する。
     *
     * </pre>
     *
     * @param transactionName データベース接続のスレッドコンテキスト上の登録名
     */
    public void setTransactionName(String transactionName) {
        assert !StringUtil.isNullOrEmpty(transactionName);
        this.transactionName = transactionName;
    }

    /** トランザクションが使用するコネクションの登録名 */
    private String transactionName = TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY;


    /**
     * 送出されてもトランザクションをコミットしなければならない例外クラスの一覧を設定する。
     * <pre>
     * 指定可能な例外は実行時例外（RuntimeExceptionのサブクラス）のみである。
     * なにも指定しなかった場合はいかなる例外についてもロールバックする。
     * </pre>
     *
     * @param exceptionClassNames 送出されてもトランザクションをコミットしなければならない例外クラスの一覧
     */
    public void setTransactionCommitExceptions(List<String> exceptionClassNames) {
        transactionCommitExceptions.addAll(
            ObjectUtil.createExceptionsClassList(exceptionClassNames)
        );
    }

    /** 送出されてもトランザクションをコミットしなければならない例外クラスの一覧 */
    private List<Class<? extends RuntimeException>>
        transactionCommitExceptions = new ArrayList<Class<? extends RuntimeException>>();

    /**
     * トランザクションの期間中に指定された例外が送出された場合、
     * 当該のトランザクションをコミットする必要があるか否かを返す。
     *
     * @param e トランザクション期間中に送出された例外オブジェクト
     * @return コミットする必要があればtrueを返す。
     */
    boolean mustBeCommittedWhenThrown(RuntimeException e) {
        for (Class<? extends RuntimeException> clazz : transactionCommitExceptions) {
            if (clazz.isAssignableFrom(e.getClass())) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     * <pre>
     * このクラスの実装では後続ハンドラに対する処理委譲の前後に、
     * データベース接続オブジェクトの初期化と終了の処理をそれぞれ行う。
     * </pre>
     */
    @SuppressWarnings("rawtypes")
    public Object handle(final Object inputData, final ExecutionContext ctx) {

        Transaction transaction = transactionFactory.getTransaction(transactionName);
        TransactionContext.setTransaction(transactionName, transaction);
        
        final List<TransactionEventCallback> listeners = prepareListeners(inputData, ctx);
        
        try {
            return (new TransactionExecutor<Object>(transaction) {
                @Override
                protected Object doInTransaction(Transaction transaction) {
                    Object result = ctx.handleNext(inputData);
                    callNormalEndHandlers(listeners, inputData, ctx);
                    return result;
                }

                @Override
                protected void onError(Transaction transaction, final Throwable throwable) {
                    if ((throwable instanceof RuntimeException)
                      && mustBeCommittedWhenThrown((RuntimeException) throwable))
                    {
                        transaction.commit();
                        callNormalEndHandlers(listeners, inputData, ctx);
                        return;
                    }
                    transaction.rollback();
                    // エラー時のコールバック処理を別トランザクションで再実行
                    (new TransactionExecutor<Void>(transaction) {
                        @Override
                        protected Void doInTransaction(Transaction transaction) {
                            callAbnormalEndHandlers(listeners, throwable, inputData, ctx);
                            transaction.commit();
                            return null;
                        }
                    }).execute();
                }
            }).execute();
            
        } finally {
            TransactionContext.removeTransaction(transactionName);
        }
    }


    @Override
    public Result handleInbound(ExecutionContext context) {
        final Transaction transaction = transactionFactory.getTransaction(transactionName);
        TransactionContext.setTransaction(transactionName, transaction);
        transaction.begin();
        return new Result.Success();
    }

    @Override
    public Result handleOutbound(ExecutionContext context) {
        if (!TransactionContext.containTransaction(transactionName)) {
            // トランザクションが開始されていない場合(beforeが失敗した場合)は何もしない
            return new Result.Success();
        }
        final Transaction transaction = TransactionContext.getTransaction(transactionName);
        TransactionContext.removeTransaction(transactionName);
        if (isCompleteTransaction(context)) {
            transaction.commit();
        } else {
            transaction.rollback();
        }
        return new Result.Success();
    }

    /**
     * トランザクションが正常終了したかどうかを判定する。<br/>
     * この実装では、ExecutionContext#isProcessSucceded() がtrueを返すかどうかで、判定を行う。
     * 
     * @param context ExecutionContext
     * @return 正常終了した場合 true
     */
    protected boolean isCompleteTransaction(ExecutionContext context) {
        return context.isProcessSucceeded();
    }
}

