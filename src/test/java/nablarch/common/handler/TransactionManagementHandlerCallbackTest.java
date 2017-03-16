package nablarch.common.handler;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.List;

import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.TransactionEventCallback;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * {@link nablarch.common.handler.TransactionManagementHandler}のテスト。
 * <p/>
 * 本クラスでは、トランザクション完了後のコールバックに着目したテストを行う。
 * トランザクション制御のみに着目したテストは、{@link TransactionManagementHandlerTest}にて実施
 *
 * @author hisaaki sioiri
 */
@RunWith(DatabaseTestRunner.class)
public class TransactionManagementHandlerCallbackTest {


    @Rule
    public SystemRepositoryResource repositoryResource = new SystemRepositoryResource(
            "nablarch/common/handler/TransactionManagementHandlerCallbackTest.xml");

    @BeforeClass
    public static void beforeClass() {
        VariousDbTestHelper.createTable(TestTable2.class);
    }

    @Before
    public void before() {
        VariousDbTestHelper.delete(TestTable2.class);
    }

    /** 正常に処理が終わった場合、コミット後に正常終了用のコールバックが呼び出されること。 */
    @Test
    public void testNormalEndCallback() {
        List<Handler<?, ?>> handlerQueue = repositoryResource.getComponent("handlerQueue");
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlerQueue);
        context.handleNext("input");

        // 正常終了用のコールバックで設定した値を確認
        assertThat(context.<String>getSessionScopedVar("normalEnd"), is("called"));
        assertThat(context.<String>getSessionScopedVar("abnormalEnd"), is(nullValue()));

        // 通常処理、正常処理で登録した2レコードが格納されていることを確認
        List<TestTable2> testTable2List = VariousDbTestHelper.findAll(TestTable2.class, "id");
        assertThat(testTable2List.size(), is(2));
        // 1レコード目
        assertThat(testTable2List.get(0).id, is("00"));
        assertThat(testTable2List.get(0).val, is("input"));
        // 2レコード目(これはコールバックの中で登録したレコード)
        assertThat(testTable2List.get(1).id, is("OK"));
        assertThat(testTable2List.get(1).val, is("1"));
    }

    /**
     * 処理が異常終了した場合、業務処理の更新がロールバックされること。
     * また、異常時のコールバック処理の更新処理がコミットされていること
     */
    @Test
    public void testAbnormalEndNonCallback() {
        List<Handler<?, ?>> handlerQueue = repositoryResource.getComponent("handlerQueue");
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlerQueue);

        try {
            context.handleNext("99");
            fail("can not be executed.");
        } catch (IllegalStateException e) {
            assertThat(true, is(true));
        }

        // 異常用のコールバックで設定した値を確認
        assertThat(context.<String>getSessionScopedVar("normalEnd"), nullValue());
        assertThat(context.<String>getSessionScopedVar("abnormalEnd"), is("called"));

        // ロールバックされて、レコードが登録されていないこと。
        List<TestTable2> testTable2List = VariousDbTestHelper.findAll(TestTable2.class, "id");
        assertThat(testTable2List.size(), is(1));
        assertThat(testTable2List.get(0).id, is("NG"));
        assertThat(testTable2List.get(0).val, is("2"));
    }

    /** テスト用のアクション */
    public static class TestAction implements Handler<Object, Object>, TransactionEventCallback<Object> {

        @Override
        public Object handle(final Object o, final ExecutionContext context) {
            AppDbConnection connection = DbConnectionContext.getConnection();
            SqlPStatement statement = connection.prepareStatement("insert into test_table2 values (?, ?)");
            statement.setString(1, "00");
            statement.setObject(2, o);
            statement.executeUpdate();
            if (o.equals("99")) {
                throw new IllegalStateException();
            }
            return o;
        }

        @Override
        public void transactionNormalEnd(final Object o, final ExecutionContext context) {
            context.setSessionScopedVar("normalEnd", "called");
            AppDbConnection connection = DbConnectionContext.getConnection();
            SqlPStatement statement = connection.prepareStatement("insert into test_table2 values (?, ?)");
            statement.setString(1, "OK");
            statement.setObject(2, "1");
            statement.executeUpdate();
        }

        @Override
        public void transactionAbnormalEnd(final Throwable throwable, final Object o, final ExecutionContext context) {
            context.setSessionScopedVar("abnormalEnd", "called");
            AppDbConnection connection = DbConnectionContext.getConnection();
            SqlPStatement statement = connection.prepareStatement("insert into test_table2 values (?, ?)");
            statement.setString(1, "NG");
            statement.setObject(2, "2");
            statement.executeUpdate();
        }
    }
}

