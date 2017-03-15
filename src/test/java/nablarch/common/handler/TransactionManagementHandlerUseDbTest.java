package nablarch.common.handler;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.BasicDbConnectionFactoryForDataSource;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.connection.TransactionManagerConnection;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.transaction.JdbcTransactionFactory;
import nablarch.core.transaction.Transaction;
import nablarch.core.transaction.TransactionContext;
import nablarch.core.transaction.TransactionFactory;
import nablarch.core.util.Builder;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import nablarch.test.support.log.app.OnMemoryLogWriter;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * {@link TransactionManagementHandler}のテスト。
 *
 * @author hisaaki sioiri
 */
@RunWith(DatabaseTestRunner.class)
public class TransactionManagementHandlerUseDbTest {

    @Rule
    public SystemRepositoryResource repositoryResource = new SystemRepositoryResource("nablarch/common/handler/TransactionManagementHandler.xml");

    /** テスト用のトランザクション名 */
    private static final String TRANSACTION_MANAGEMENT_HANDLER_TEST = "transactionManagementHandlerTest";

    private TransactionManagementHandler handler;

    @BeforeClass
    public static void setUpClass() {
        VariousDbTestHelper.createTable(TestTable.class);
    }

    @Before
    public void setUp() {
        BasicDbConnectionFactoryForDataSource source = repositoryResource.getComponent("connectionFactory");
        TransactionManagerConnection connection = source.getConnection(TRANSACTION_MANAGEMENT_HANDLER_TEST);
        DbConnectionContext.setConnection(TRANSACTION_MANAGEMENT_HANDLER_TEST, connection);
        handler = repositoryResource.getComponent("TransactionManagementHandler");

        // テストデータの準備
        VariousDbTestHelper.setUpTable(new TestTable("00001"));

        // ログのクリア
        OnMemoryLogWriter.clear();
    }

    @After
    public void tearDown() throws Exception {
        final TransactionManagerConnection connection = (TransactionManagerConnection) DbConnectionContext.getConnection(
                TRANSACTION_MANAGEMENT_HANDLER_TEST);
        connection.terminate();
        DbConnectionContext.removeConnection(TRANSACTION_MANAGEMENT_HANDLER_TEST);
    }

    @AfterClass
    public static void afterClass() {
        // 最後にゴミデータを削除する。
        TransactionContext.removeTransaction(
                TRANSACTION_MANAGEMENT_HANDLER_TEST);
    }

    /**
     * {@link TransactionManagementHandler#setTransactionCommitExceptions(java.util.List)}の正常系テスト。
     *
     * @throws Exception
     */
    @Test
    public void testSetTransactionCommitExceptions() throws Exception {
        //*********************************************************************
        // コミット対象のExceptionに1クラスを指定
        //*********************************************************************
        TransactionManagementHandler oneClass = new TransactionManagementHandler();
        oneClass.setTransactionCommitExceptions(
                Builder.list(String.class, RuntimeException1.class.getName()));
        assertThat("指定したクラスであるためtrue", oneClass.mustBeCommittedWhenThrown(
                new RuntimeException1()),
                is(true));
        assertThat("指定したクラスのサブクラスのためtrue", oneClass.mustBeCommittedWhenThrown(
                new RuntimeException1Sub()),
                is(true));
        assertThat("指定したクラス(サブクラス含む)以外のExceptionの場合は、false",
                oneClass.mustBeCommittedWhenThrown(new NullPointerException()),
                is(false));
        //*********************************************************************
        // コミット対象のExceptionに複数クラスを指定
        //*********************************************************************
        TransactionManagementHandler multiClass = new TransactionManagementHandler();
        multiClass.setTransactionCommitExceptions(Builder.list(String.class,
                RuntimeException1.class.getName(),
                RuntimeException2.class.getName()
        ));
        assertThat("指定したクラスはtrue", multiClass.mustBeCommittedWhenThrown(
                new RuntimeException1()), is(true));
        assertThat("指定したクラスはtrue", multiClass.mustBeCommittedWhenThrown(
                new RuntimeException2()), is(true));
        assertThat("サブクラスもtrue", multiClass.mustBeCommittedWhenThrown(
                new RuntimeException1Sub()), is(true));
        assertThat("指定したクラスの親クラスはfalse", multiClass.mustBeCommittedWhenThrown(
                new RuntimeException()), is(false));
    }

    /** {@link TransactionManagementHandler#setTransactionCommitExceptions(java.util.List)}の異常系テスト。 */
    @Test
    public void testSetTransactionCommitExceptionsError() throws Exception {
        //*********************************************************************
        // 存在しない例外クラスを指定
        //*********************************************************************
        TransactionManagementHandler error1 = new TransactionManagementHandler();
        try {
            error1.setTransactionCommitExceptions(Builder.list(String.class,
                    "HogeException"));
            fail("does not run.");
        } catch (Exception e) {
            assertThat(e, instanceOf(RuntimeException.class));
        }
        //*********************************************************************
        // RuntimeException意外を指定
        //*********************************************************************
        TransactionManagementHandler error2 = new TransactionManagementHandler();
        try {
            error2.setTransactionCommitExceptions(
                    Builder.list(String.class,
                            ClassNotFoundException.class.getName()));
            fail("does not run.");
        } catch (Exception e) {
            assertThat(e.getMessage(), is(
                    "this class isn't a subclass of java.lang.RuntimeException.: "
                            + ClassNotFoundException.class.getName()));
        }
    }

    /**
     * {@link TransactionManagementHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * 正常に処理が終わった場合、正しくコミット処理が行われていること。
     *
     * @throws Exception
     */
    @Test
    public void testHandle1() throws Exception {
        // テストターゲットの呼び出し
        ExecutionContext context = new ExecutionContext();
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();

        // テストデータをhogeに更新するハンドラを追加
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                AppDbConnection connection = DbConnectionContext.getConnection(
                        TRANSACTION_MANAGEMENT_HANDLER_TEST);
                SqlPStatement statement = connection.prepareStatement(
                        "update test_table set col1 = 'hoge'");
                statement.executeUpdate();
                return new Object();
            }
        });
        context.addHandlers(handlers);

        // テスト対象のハンドラの実行
        handler.handle(null, context);

        // コミットされていることを確認
        List<TestTable> testTableList = VariousDbTestHelper.findAll(TestTable.class);
        assertThat("件数は1件であること", testTableList.size(), is(1));
        assertThat("hogeに更新されていること", testTableList.get(0).col1, is("hoge"));

        // コンテキストから対象オブジェクトが削除されていること
        assertRemoveTransactionContext();
    }

    /**
     * {@link TransactionManagementHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * コミット対象の例外が発生した場合、正しくコミット処理が行われていること。
     *
     * @throws Exception
     */
    @Test
    public void testHandle2() throws Exception {
        ExecutionContext context = new ExecutionContext();
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                AppDbConnection connection = DbConnectionContext.getConnection(
                        TRANSACTION_MANAGEMENT_HANDLER_TEST);
                SqlPStatement statement = connection.prepareStatement(
                        "update test_table set col1 = 'hoge2'");
                statement.executeUpdate();
                // コミット対象の例外を送出
                throw new NullPointerException();
            }
        });
        context.addHandlers(handlers);

        try {
            handler.handle(null, context);
            fail("does not run.");
        } catch (Exception e) {
            assertThat(e, instanceOf(NullPointerException.class));
        }

        // コミットされていることを確認
        List<TestTable> testTableList = VariousDbTestHelper.findAll(TestTable.class);
        assertThat("件数は1件であること", testTableList.size(), is(1));
        assertThat("hoge2に更新されていること", testTableList.get(0).col1, is("hoge2"));

        // コンテキストから対象オブジェクトが削除されていること
        assertRemoveTransactionContext();
    }

    /**
     * {@link TransactionManagementHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * コミット対象以外の例外が発生した場合、ロールバックされること。
     *
     * @throws Exception
     */
    @Test
    public void testHandle3() throws Exception {
        ExecutionContext context = new ExecutionContext();
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                AppDbConnection connection = DbConnectionContext.getConnection(
                        TRANSACTION_MANAGEMENT_HANDLER_TEST);
                SqlPStatement statement = connection.prepareStatement(
                        "update test_table set col1 = 'hoge2'");
                statement.executeUpdate();
                // コミット対象の例外を送出
                throw new RuntimeException();
            }
        });
        context.addHandlers(handlers);

        try {
            handler.handle(null, context);
            fail("does not run.");
        } catch (Exception e) {
            assertThat(e, instanceOf(RuntimeException.class));
        }

        // ロールバックされていることを確認
        List<TestTable> testTableList = VariousDbTestHelper.findAll(TestTable.class);
        assertThat("件数は1件であること", testTableList.size(), is(1));
        assertThat("更新されずに'00001'のままであること", testTableList.get(0).col1, is("00001"));

        // コンテキストから対象オブジェクトが削除されていること
        assertRemoveTransactionContext();
    }

    /**
     * {@link TransactionManagementHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * Errorのサブクラスの場合は、ロールバックされること
     *
     * @throws Exception
     */
    @Test
    public void testHandle4() throws Exception {
        ExecutionContext context = new ExecutionContext();
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                AppDbConnection connection = DbConnectionContext.getConnection(
                        TRANSACTION_MANAGEMENT_HANDLER_TEST);
                SqlPStatement statement = connection.prepareStatement(
                        "update test_table set col1 = 'hoge2'");
                statement.executeUpdate();
                // Errorを発生
                throw new OutOfMemoryError();
            }
        });
        context.addHandlers(handlers);

        try {
            handler.handle(null, context);
            fail("does not run.");
        } catch (Error e) {
            assertThat(e, instanceOf(OutOfMemoryError.class));
        }

        // ロールバックされていることを確認
        List<TestTable> testTableList = VariousDbTestHelper.findAll(TestTable.class);
        assertThat("件数は1件であること", testTableList.size(), is(1));
        assertThat("更新されずに'00001'のままであること", testTableList.get(0).col1, is("00001"));

        // コンテキストから対象オブジェクトが削除されていること
        assertRemoveTransactionContext();
    }

    /**
     * {@link TransactionManagementHandler#handle(Object, nablarch.fw.ExecutionContext)}の異常系テスト。
     * ケース内容:<br>
     * <ul><li>ハンドラでRuntimeExceptionが発生</li></ul>
     * 期待値:<br/>
     * <ul><li>ハンドラで発生したRuntimeExceptionがthrowされてくる</li></ul>
     *
     * @throws Exception
     */
    @Test
    public void testHandleError1() throws Exception {
        ExecutionContext context = new ExecutionContext();
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                throw new IllegalArgumentException("IllegalArgumentException.");
            }
        });
        context.addHandlers(handlers);

        try {
            handler.handle(null, context);
            fail("does not run.");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is("IllegalArgumentException."));
        }

        // ログが出力されていないこと
        assertThat(OnMemoryLogWriter.getMessages("writer.transaction").size(), is(0));

        // コンテキストから対象オブジェクトが削除されていること
        assertRemoveTransactionContext();
    }

    /**
     * {@link TransactionManagementHandler#handle(Object, nablarch.fw.ExecutionContext)}の異常系テスト。
     * ケース内容:<br>
     * <ul><li>ハンドラでErrorが発生</li></ul>
     * 期待値:<br/>
     * <ul><li>ハンドラで発生したErrorがthrowされてくる</li></ul>
     *
     * @throws Exception
     */
    @Test
    public void testHandleError2() throws Exception {
        ExecutionContext context = new ExecutionContext();
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                throw new NoClassDefFoundError("NoClassDefFoundError.");
            }
        });
        context.addHandlers(handlers);

        try {
            handler.handle(null, context);
            fail("does not run.");
        } catch (Error e) {
            assertThat(e.getMessage(), is("NoClassDefFoundError."));
        }

        // ログが出力されていないこと
        assertThat(OnMemoryLogWriter.getMessages("writer.transaction").size(), is(0));

        // コンテキストから対象オブジェクトが削除されていること
        assertRemoveTransactionContext();
    }

    /**
     * {@link TransactionManagementHandler#handle(Object, nablarch.fw.ExecutionContext)}の異常系テスト。
     * ケース内容:<br>
     * <ul><li>ハンドラでRuntimeExceptionが発生して、ロールバック時にもRuntimeExceptionが発生する場合</li></ul>
     * 期待値:<br/>
     * <ul>
     * <li>ロールバック時に発生した例外がthrowされる。</li>
     * <li>ハンドラで発生した例外は、WARNレベルでログ出力される。</li>
     * </ul>
     *
     * @throws Exception
     */
    @Test
    public void testHandleError3() throws Exception {
        ExecutionContext context = new ExecutionContext();
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                throw new RuntimeException("handler runtime exception.");
            }
        });
        context.addHandlers(handlers);

        handler.setTransactionFactory(new ErrorJdbcTransactionFactory());

        try {
            handler.handle(null, context);
            fail("does not run.");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is("RuntimeException."));
        }

        // ログのアサート
        assertWarnLog(
                "java.lang.RuntimeException.*handler runtime exception\\.");

        // コンテキストから対象オブジェクトが削除されていること
        assertRemoveTransactionContext();
    }

    /**
     * {@link TransactionManagementHandler#handle(Object, nablarch.fw.ExecutionContext)}の異常系テスト。
     * ケース内容:<br>
     * <ul><li>ハンドラでRuntimeExceptionが発生して、ロールバック時にErrorが発生する場合</li></ul>
     * 期待値:<br/>
     * <ul>
     * <li>ロールバック時に発生した例外がthrowされる。</li>
     * <li>ハンドラで発生した例外は、WARNレベルでログ出力される。</li>
     * </ul>
     *
     * @throws Exception
     */
    @Test
    public void testHandleError4() throws Exception {
        ExecutionContext context = new ExecutionContext();
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                throw new RuntimeException("handler runtime exception!");
            }
        });
        context.addHandlers(handlers);

        ErrorJdbcTransactionFactory factory = new ErrorJdbcTransactionFactory();
        factory.setError(true);
        handler.setTransactionFactory(factory);

        try {
            handler.handle(null, context);
            fail("does not run.");
        } catch (Error e) {
            assertThat(e.getMessage(), is("Error."));
        }

        // ログのアサート
        assertWarnLog("java.lang.RuntimeException.*handler runtime exception!");

        // コンテキストから対象オブジェクトが削除されていること
        assertRemoveTransactionContext();
    }

    /**
     * {@link TransactionManagementHandler#handle(Object, nablarch.fw.ExecutionContext)}の異常系テスト。
     * ケース内容:<br>
     * <ul><li>ハンドラでErrorが発生して、ロールバック時にRuntimeExceptionが発生する場合</li></ul>
     * 期待値:<br/>
     * <ul>
     * <li>ロールバック時に発生した例外がthrowされる。</li>
     * <li>ハンドラで発生した例外は、WARNレベルでログ出力される。</li>
     * </ul>
     *
     * @throws Exception
     */
    @Test
    public void testHandleError5() throws Exception {
        ExecutionContext context = new ExecutionContext();
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                throw new Error("handler error!");
            }
        });
        context.addHandlers(handlers);

        ErrorJdbcTransactionFactory factory = new ErrorJdbcTransactionFactory();
        handler.setTransactionFactory(factory);

        try {
            handler.handle(null, context);
            fail("does not run.");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is("RuntimeException."));
        }

        // ログのアサート
        assertWarnLog("java.lang.Error.*handler error!");

        // コンテキストから対象オブジェクトが削除されていること
        assertRemoveTransactionContext();
    }

    /**
     * {@link TransactionManagementHandler#handle(Object, nablarch.fw.ExecutionContext)}の異常系テスト。
     * ケース内容:<br>
     * <ul><li>ハンドラでErrorが発生して、ロールバック時にErrorが発生する場合</li></ul>
     * 期待値:<br/>
     * <ul>
     * <li>ロールバック時に発生した例外がthrowされる。</li>
     * <li>ハンドラで発生した例外は、WARNレベルでログ出力される。</li>
     * </ul>
     *
     * @throws Exception
     */
    @Test
    public void testHandleError6() throws Exception {
        ExecutionContext context = new ExecutionContext();
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                throw new Error("handler error.");
            }
        });
        context.addHandlers(handlers);

        ErrorJdbcTransactionFactory factory = new ErrorJdbcTransactionFactory();
        factory.setError(true);
        handler.setTransactionFactory(factory);

        try {
            handler.handle(null, context);
            fail("does not run.");
        } catch (Error e) {
            assertThat(e.getMessage(), is("Error."));
        }

        // ログのアサート
        assertWarnLog("java.lang.Error.*handler error.");

        // コンテキストから対象オブジェクトが削除されていること
        assertRemoveTransactionContext();
    }

    private static class ErrorJdbcTransactionFactory implements TransactionFactory {

        private JdbcTransactionFactory factory;

        private boolean isError;

        private ErrorJdbcTransactionFactory() {
            this.factory = new JdbcTransactionFactory();
        }

        public Transaction getTransaction(String connectionName) {
            return new ErrorJdbcTransaction(factory.getTransaction(
                    connectionName), isError);
        }

        public void setIsolationLevel(String isolationLevel) {
            factory.setIsolationLevel(isolationLevel);
        }

        public void setInitSqlList(List<String> initSqlList) {
            factory.setInitSqlList(initSqlList);
        }

        public void setError(boolean error) {
            isError = error;
        }
    }

    private static class ErrorJdbcTransaction implements Transaction {

        private Transaction transaction;

        private boolean error;

        private ErrorJdbcTransaction(Transaction transaction, boolean isError) {
            this.transaction = transaction;
            error = isError;
        }

        public void begin() {
            transaction.begin();
        }

        public void commit() {
            transaction.commit();
        }

        public void rollback() {
            transaction.rollback();
            if (error) {
                throw new Error("Error.");
            } else {
                throw new RuntimeException("RuntimeException.");
            }
        }
    }

    /**
     * ワーニングログをアサートする。
     *
     * @param message ログのメッセージ
     */
    private static void assertWarnLog(String message) {
        List<String> log = OnMemoryLogWriter.getMessages("writer.memory");
        System.out.println("log = " + log);
        boolean writeLog = false;
        for (String logMessage : log) {
            String str = logMessage.replaceAll("[\\r\\n]", "");
            if (str.matches(
                    "^WARN transaction has failed\\."
                            + ".*" + message + ".*$")) {
                writeLog = true;
            }
        }
        assertThat("元例外がWARNレベルでログに出力されていること", writeLog, is(true));
    }

    /** TransactionContextからremoveされたことをアサートする。 */
    private static void assertRemoveTransactionContext() {
        try {
            TransactionContext.getTransaction(
                    TRANSACTION_MANAGEMENT_HANDLER_TEST);
            fail("does not run.");
        } catch (Exception e) {
            // nop
        }
    }

    //*************************************************************************
    // 以下テスト用の例外クラス
    //*************************************************************************
    private static class RuntimeException1 extends RuntimeException {

    }

    private static class RuntimeException1Sub extends RuntimeException1 {

    }

    private static class RuntimeException2 extends RuntimeException {

    }
}
