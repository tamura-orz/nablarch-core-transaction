package nablarch.common.handler;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.SqlResultSet;
import nablarch.core.transaction.TransactionTimeoutException;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * {@link TransactionManagementHandler}のトランザクションタイムアウトに着目したテスト。
 *
 * @author hisaaki sioiri
 */
@RunWith(DatabaseTestRunner.class)
public class TransactionManagementHandlerTransactionTimeoutTest {

    @Rule
    public SystemRepositoryResource repositoryResource = new SystemRepositoryResource(
            "nablarch/common/handler/TransactionManagementHandlerTransactionTimeoutTest.xml");

    /** テスト用のトランザクション名 */
    private static final String TEST_TRANSACTION_NAME = "testTransactionName";

    @BeforeClass
    public static void setUpClass() {
        VariousDbTestHelper.createTable(TestTable2.class);
        VariousDbTestHelper.setUpTable(new TestTable2("01", "1"));
    }

    /**
     * トランザクションタイムアウトが発生しないケース。
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testNormalEnd() throws Exception {
        List<Handler<?, ?>> handlerQueue = new ArrayList<Handler<?, ?>>(
                (Collection<? extends Handler<?, ?>>) repositoryResource.getComponent("handlerQueue"));
        handlerQueue.add(new TransactionManagementHandlerTransactionTimeoutTest.NormalAction());
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlerQueue);
        Object o = context.handleNext(new Object());
        assertThat(o, is(instanceOf(SqlResultSet.class)));
    }

    /**
     * トランザクションタイムアウトが発生する場合。
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testTransactionTimeout() throws Exception {
        List<Handler<?, ?>> handlerQueue = new ArrayList<Handler<?, ?>>(
                (Collection<? extends Handler<?, ?>>) repositoryResource.getComponent("handlerQueue"));
        handlerQueue.add(new TransactionManagementHandlerTransactionTimeoutTest.TransactionTimeoutAction());
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlerQueue);
        try {
            context.handleNext(new Object());
            fail("ここはとおらない。");
        } catch (TransactionTimeoutException e) {
            e.printStackTrace();
            assertThat(e.getMessage(), is(containsString("transaction was timeout.")));
        }
    }

    /**
     * テスト用のアクション。
     * このアクションではタイムアウトは発生しない。
     */
    private static class NormalAction implements Handler<Object, Object> {

        public Object handle(Object o, ExecutionContext context) {
            AppDbConnection connection = DbConnectionContext.getConnection(TEST_TRANSACTION_NAME);
            SqlPStatement statement = connection.prepareStatement("SELECT * FROM TEST_TABLE2");
            SqlResultSet sqlRows = statement.retrieve();
            System.out.println("sqlRows = " + sqlRows);
            return sqlRows;
        }
    }

    /**
     * トランザクションタイムアウトを発生させるアクション。
     */
    private static class TransactionTimeoutAction implements Handler<Object, Object> {

        public Object handle(Object o, ExecutionContext context) {
            AppDbConnection connection = DbConnectionContext.getConnection(TEST_TRANSACTION_NAME);
            SqlPStatement statement = connection.prepareStatement("SELECT * FROM TEST_TABLE2");
            SqlResultSet sqlRows = statement.retrieve();
            System.out.println("sqlRows = " + sqlRows);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            SqlResultSet sqlRows2 = statement.retrieve();
            System.out.println("sqlRows2 = " + sqlRows2);
            return o;
        }
    }
}

