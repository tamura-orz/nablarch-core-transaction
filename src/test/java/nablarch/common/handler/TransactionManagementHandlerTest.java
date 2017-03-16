package nablarch.common.handler;

import static org.hamcrest.CoreMatchers.*;

import nablarch.core.transaction.Transaction;
import nablarch.core.transaction.TransactionFactory;
import nablarch.fw.ExecutionContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;

public class TransactionManagementHandlerTest {

    private TransactionManagementHandler target = new TransactionManagementHandler();
    
    @Mocked
    private TransactionFactory transactionFactory;

    @Mocked
    private Transaction transaction;

    @Mocked
    private ExecutionContext context;

    @Before
    public void setup() {
        target.setTransactionName("tran");
        target.setTransactionFactory(transactionFactory);
        
        context.setProcessSucceeded(true);
    }
    @Test
    public void test() {
        {
            // 正常系
            new Expectations() {{
                transactionFactory.getTransaction("tran");
                result = transaction;
                context.isProcessSucceeded();
                result = true;
            }};
            
            Assert.assertThat(target.handleInbound(context)
                                    .isSuccess(), is(true));

            new Verifications() {{
                transactionFactory.getTransaction("tran");
                times = 1;
                
                transaction.begin();
                times = 1;
            }};            

            Assert.assertThat(target.handleOutbound(context)
                                    .isSuccess(), is(true));
            
            new Verifications() {{
                transaction.commit();
                times = 1;
                transaction.rollback();
                times = 0;
            }};            
            
        }
        {
            // 異常系
            new Expectations() {{
                transactionFactory.getTransaction("tran");
                result = transaction;
                context.isProcessSucceeded();
                result = false;
            }};
            
            Assert.assertThat(target.handleInbound(context)
                                    .isSuccess(), is(true));

            new Verifications() {{
                transactionFactory.getTransaction("tran");
                times = 1;
                
                transaction.begin();
                times = 1;
            }};            

            Assert.assertThat(target.handleOutbound(context)
                                    .isSuccess(), is(true));
            
            new Verifications() {{
                transaction.commit();
                times = 0;
                transaction.rollback();
                times = 1;
            }};            
            
        }
        {
            new Expectations() {{
                transactionFactory.getTransaction("tran");
                result = transaction;
                minTimes = 0;
            }};

            // トランザクション開始前に Outbound が呼ばれた場合
            Assert.assertThat(target.handleOutbound(context)
                                    .isSuccess(), is(true));
            
            new Verifications() {{
                transaction.commit();
                times = 0;
                transaction.rollback();
                times = 0;
            }};            
        }
    }

}
