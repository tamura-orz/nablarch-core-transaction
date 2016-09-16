package nablarch.common.handler;

import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;
import nablarch.core.transaction.Transaction;
import nablarch.core.transaction.TransactionFactory;
import nablarch.fw.ExecutionContext;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

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
            new NonStrictExpectations() {{
                transactionFactory.getTransaction("tran");
                result = transaction;
                context.isProcessSucceeded();
                result = true;
            }};
            
            assertTrue(target.handleInbound(context).isSuccess());

            new Verifications() {{
                transactionFactory.getTransaction("tran");
                times = 1;
                
                transaction.begin();
                times = 1;
            }};            

            assertTrue(target.handleOutbound(context).isSuccess());
            
            new Verifications() {{
                transaction.commit();
                times = 1;
                transaction.rollback();
                times = 0;
            }};            
            
        }
        {
            // 異常系
            new NonStrictExpectations() {{
                transactionFactory.getTransaction("tran");
                result = transaction;
                context.isProcessSucceeded();
                result = false;
            }};
            
            assertTrue(target.handleInbound(context).isSuccess());

            new Verifications() {{
                transactionFactory.getTransaction("tran");
                times = 1;
                
                transaction.begin();
                times = 1;
            }};            

            assertTrue(target.handleOutbound(context).isSuccess());
            
            new Verifications() {{
                transaction.commit();
                times = 0;
                transaction.rollback();
                times = 1;
            }};            
            
        }
        {
            new NonStrictExpectations() {{
                transactionFactory.getTransaction("tran");
                result = transaction;
            }};

            // トランザクション開始前に Outbound が呼ばれた場合
            assertTrue(target.handleOutbound(context).isSuccess());
            
            new Verifications() {{
                transaction.commit();
                times = 0;
                transaction.rollback();
                times = 0;
            }};            
        }
    }

}
