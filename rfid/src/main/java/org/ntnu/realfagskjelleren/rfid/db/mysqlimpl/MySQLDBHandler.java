package org.ntnu.realfagskjelleren.rfid.db.mysqlimpl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ntnu.realfagskjelleren.rfid.db.model.DBHandler;
import org.ntnu.realfagskjelleren.rfid.db.model.Transaction;
import org.ntnu.realfagskjelleren.rfid.db.model.User;
import org.ntnu.realfagskjelleren.rfid.settings.Settings;

import java.sql.*;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * @author Håvard Slettvold
 */
public class MySQLDBHandler implements DBHandler {

    private static Logger logger = LogManager.getLogger(MySQLDBHandler.class.getName());

    private Settings settings;

    private final String RFID_DATABASE_VERSION = "2.0";

    // Query Strings
    private final String CREATE_USER_QS = "INSERT INTO user (credit, rfid, ecc, is_staff, created) VALUES (0, ?, ?, 0, NOW());";
    private final String UPDATE_USER_RFID_QS = "UPDATE user SET rfid = ? WHERE id = ?";
    private final String GET_USER_RFID_QS = "SELECT * FROM user WHERE rfid = ?;";
    private final String GET_USER_ECC_QS = "SELECT * FROM user WHERE ecc = ?;";
    private final String EXISTS_RFID_QS = "SELECT COUNT(*) FROM user WHERE rfid = ?;";
    private final String EXISTS_ECC_QS = "SELECT COUNT(*) FROM user WHERE ecc = ?;";

    private final String DEPOSIT_QS = "UPDATE user SET credit = credit+? WHERE rfid = ?;";
    private final String DEDUCT_QS = "UPDATE user SET credit = credit-? WHERE rfid = ?;";

    private final String GET_TRANSACTIONS_QS = "SELECT * FROM transaction ORDER BY rowid DESC LIMIT ?;";
    private final String GET_ALL_USERS_QS = "SELECT * FROM user;";

    private final String TRANSACTION_QS = "INSERT INTO transaction (user_id, value, is_deposit, new_balance) VALUES (?, ?, ?, ?);";
    private final String LOG_QS = "INSERT INTO log (message, date) VALUES (?, datetime('now'));";

    public MySQLDBHandler(Settings settings) {
        this.settings = settings;
    }

    public boolean testConnection() {
        boolean success = false;

        Connection con = null;
        Statement st = null;
        ResultSet rs = null;

        try {
            con = getConnection();
            st = con.createStatement();
            rs = st.executeQuery("SELECT VERSION()");

            if (rs.next()) {
                success = true;
                logger.debug("MySQL server running version: " + rs.getString(1));
            }
        } catch (SQLException e) {
            logger.error(e.getMessage());
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (st != null) {
                    st.close();
                }
                if (con != null) {
                    con.close();
                }

            } catch (SQLException ex) {
                logger.warn(ex.getMessage(), ex);
            }
        }

        createDatabase();

        return success;
    }

    public boolean createDatabase() {
        boolean success = false;
        String createUserSQL =
                "CREATE TABLE IF NOT EXISTS `user` (" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT," +
                "  `rfid` varchar(20) NOT NULL UNIQUE," +
                "  `ecc` int(11) NOT NULL," +
                "  `credit` int(11) NOT NULL," +
                "  `is_staff` tinyint(1) NOT NULL DEFAULT '0'," +
                "  `created` datetime NOT NULL," +
                "  `last_used` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`id`)" +
                ") ENGINE=InnoDB  DEFAULT CHARSET=latin1;";
        String createTransactionSQL =
                "CREATE TABLE IF NOT EXISTS `transaction` (" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT," +
                "  `user_id` int(11) NOT NULL," +
                "  `value` int(11) NOT NULL," +
                "  `is_deposit` tinyint(1) NOT NULL DEFAULT '0'," +
                "  `new_balance` int(11) NOT NULL," +
                "  `date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`id`)," +
                "  INDEX user_ind (user_id)," +
                "  FOREIGN KEY (user_id)" +
                "      REFERENCES user(id)" +
                "      ON DELETE CASCADE" +
                ") ENGINE=InnoDB  DEFAULT CHARSET=latin1;";
        String createVersionSQL =
                "CREATE TABLE IF NOT EXISTS `version` (" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT," +
                "  `version` varchar(20) NOT NULL," +
                "  `executed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`id`)" +
                ") ENGINE=InnoDB  DEFAULT CHARSET=latin1;";
        String createLogSQL =
                "CREATE TABLE IF NOT EXISTS `log` (" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT," +
                "  `message` varchar(200) NOT NULL," +
                "  `date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`id`)" +
                ") ENGINE=InnoDB  DEFAULT CHARSET=latin1;";

        try (Connection con = getConnection();
             Statement st = con.createStatement()) {

            st.executeUpdate(createUserSQL);
            st.executeUpdate(createTransactionSQL);
            st.executeUpdate(createVersionSQL);
            st.executeUpdate(createLogSQL);

            success = true;

        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return success;
    }

    private Connection getConnection() throws SQLException {
        String url = String.format("jdbc:mysql://%s:%s/%s", settings.getDbHost(), settings.getDbPort(), settings.getDbName());
        return DriverManager.getConnection(url, settings.getDbUsername(), settings.getDbPassword());
    }


    /**
     * This method creates a {@link User} object based on database information.
     * If no user matches the given rfid, one will be created first.
     *
     * @param rfid Lookup parameter for a user
     * @return {@link User} object
     * @throws java.sql.SQLException
     */
    @Override
    public User get_or_create(String rfid) throws SQLException {
        try (Connection con = getConnection()) {
            if (!rfid_exists(rfid)) {
                // If it doesn't exist, create
                try (PreparedStatement ps = con.prepareStatement(CREATE_USER_QS)) {
                    ps.setString(1, rfid);
                    ps.setInt(2, makeECC());
                    ps.executeUpdate();
                    ps.close();
                }
            }

            try (PreparedStatement ps = con.prepareStatement(GET_USER_RFID_QS)) {
                ps.setString(1, rfid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        User user = new User(rs.getInt("id"), rs.getString("rfid"), rs.getBoolean("is_staff"), rs.getInt("credit"), rs.getTimestamp("last_used"));
                        return user;
                    }
                }
            }
        } catch (SQLException ex) {
            logger.error(String.format("Failed to retrieve or create user for RFID '%s'.", rfid));
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        return null;
    }

    /**
     * Finds a {@link User} based on that user's ECC number.
     * The function of ECC is to give users a small token which identifies their
     * account, in case of card loss or other needs for replacement.
     *
     * @param ecc ECC String
     * @return {@link User} object that matched ECC or null
     * @throws SQLException
     */
    @Override
    public User get_user(int ecc) throws SQLException {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(GET_USER_ECC_QS)) {

            ps.setInt(1, ecc);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = new User(rs.getInt("id"), rs.getString("rfid"), rs.getBoolean("is_staff"), rs.getInt("credit"), rs.getTimestamp("last_used"));
                    return user;
                }
            }
        } catch (SQLException ex) {
            logger.error(String.format("Failed to retrieve user for ECC '%d'.", ecc));
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        return null;
    }

    @Override
    public void update_user_rfid(int user_id, String rfid) throws SQLException {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE_USER_RFID_QS)) {

            ps.setString(1, rfid);
            ps.setInt(2, user_id);
            ps.executeUpdate();

        } catch (SQLException ex) {
            logger.error("Failed to update RFID.");
            logger.error(ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Check if a rfid matches a {@link User}
     *
     * @param rfid RFID to check
     * @return True if RFID belongs to a {@link User}
     * @throws SQLException
     */
    @Override
    public boolean rfid_exists(String rfid) throws SQLException {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(EXISTS_RFID_QS)) {

            ps.setString(1, rfid);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt(1) == 0) return false;
            }

        } catch (SQLException ex) {
            logger.error(String.format("Failed to retrieve user for RFID '%s'.", rfid));
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        return true;
    }

    /**
     * Check if an ECC matches a {@link User}
     *
     * @param ecc ECC number to check
     * @return True if RFID belongs to a {@link User}
     * @throws SQLException
     */
    @Override
    public boolean ecc_exists(int ecc) throws SQLException {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(EXISTS_ECC_QS)) {

            ps.setInt(1, ecc);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt(1) == 0) return false;
            }

        } catch (SQLException ex) {
            logger.error(String.format("Failed to retrieve user for ECC '%d'.", ecc));
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        return true;
    }

    @Override
    public void deposit(String rfid, int value) throws SQLException {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(DEPOSIT_QS)) {

            ps.setInt(1, value);
            ps.setString(2, rfid);
            ps.executeUpdate();

        } catch (SQLException ex) {
            logger.error(String.format("Failed to deposit amount to RFID '%s'.", rfid));
            logger.error(ex.getMessage(), ex);
            throw ex;
        }
    }

    @Override
    public void deduct(String rfid, int value) throws SQLException {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(DEDUCT_QS)) {

            ps.setInt(1, value);
            ps.setString(2, rfid);
            ps.executeUpdate();

        } catch (SQLException ex) {
            logger.error(String.format("Failed to deduct amount from RFID '%s'.", rfid));
            logger.error(ex.getMessage(), ex);
            throw ex;
        }
    }

    @Override
    public List<User> getAllUsers() throws SQLException {
        return null;
    }

    @Override
    public List<Transaction> getTransactions(int... amount) throws SQLException {
        return null;
    }

    /**
     * Transactions done are logged with this method.
     *
     * @param user_id ID of {@link User} for this transaction
     * @param value Amount of money
     * @param is_deposit True if money was deposited
     * @param new_balance The new balance for {@link User}
     * @throws SQLException
     */
    @Override
    public void transaction(int user_id, int value, boolean is_deposit, int new_balance) throws SQLException {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(TRANSACTION_QS)) {

            ps.setInt(1, user_id);
            ps.setInt(2, value);
            ps.setBoolean(3, is_deposit);
            ps.setInt(4, new_balance);
            ps.executeUpdate();

        } catch (SQLException ex) {
            logger.error(String.format("Failed to create transaction for %d to User '%d'. Deposit = %s", value, user_id, is_deposit));
            logger.error(ex.getMessage(), ex);
            throw ex;
        }
    }

    @Override
    public void log(String message) throws SQLException {

    }

    /**
     * Creates an ECC number meant to be used as simple account recovery.
     *
     * @return random int in range
     * @throws SQLException
     */
    private int makeECC() throws SQLException {
        Random rand = new Random();
        int ecc;
        do {
            ecc = rand.nextInt((999999 - 100000) + 1) + 100000;
        } while (ecc_exists(ecc));
        return ecc;
    }

}
