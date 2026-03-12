import java.sql.*;
public class QueryOrders {
  public static void main(String[] args) throws Exception {
    String url = "jdbc:mysql://localhost:3306/flower_shop?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    try (Connection conn = DriverManager.getConnection(url, "root", "root");
         PreparedStatement ps = conn.prepareStatement("SELECT order_no,status,total_amount,payment_amount FROM customer_order ORDER BY id DESC LIMIT 10");
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        System.out.println(rs.getString(1) + "\t" + rs.getString(2) + "\t" + rs.getBigDecimal(3) + "\t" + rs.getBigDecimal(4));
      }
    }
  }
}