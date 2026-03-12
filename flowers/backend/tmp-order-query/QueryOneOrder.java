import java.sql.*;
public class QueryOneOrder {
  public static void main(String[] args) throws Exception {
    String orderNo = args.length > 0 ? args[0] : "";
    String url = "jdbc:mysql://localhost:3306/flower_shop?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    try (Connection conn = DriverManager.getConnection(url, "root", "root");
         PreparedStatement ps = conn.prepareStatement("SELECT order_no,status,total_amount,payment_amount,completed_at,shipped_at,pay_time FROM customer_order WHERE order_no = ?")) {
      ps.setString(1, orderNo);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          System.out.println(rs.getString(1) + "\t" + rs.getString(2) + "\t" + rs.getBigDecimal(3) + "\t" + rs.getBigDecimal(4) + "\t" + rs.getTimestamp(5) + "\t" + rs.getTimestamp(6) + "\t" + rs.getTimestamp(7));
        }
      }
    }
  }
}