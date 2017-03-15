package nablarch.common.handler;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * テストテーブル2
 */
@Entity
@Table(name = "TEST_TABLE2")
public class TestTable2 {
    
    public TestTable2() {
    };
    
    public TestTable2(String id, String val) {
        this.id = id;
        this.val = val;
    }

    @Id
    @Column(name = "ID", length = 2, nullable = false)
    public String id;
    
    @Column(name = "VAL", length = 100)
    public String val;
}
