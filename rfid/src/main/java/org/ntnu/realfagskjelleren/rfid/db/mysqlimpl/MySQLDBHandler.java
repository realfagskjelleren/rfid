package org.ntnu.realfagskjelleren.rfid.db.mysqlimpl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ntnu.realfagskjelleren.rfid.db.model.DBHandler;
import org.ntnu.realfagskjelleren.rfid.db.model.Transaction;
import org.ntnu.realfagskjelleren.rfid.db.model.User;
import org.ntnu.realfagskjelleren.rfid.db.model.Version;
import org.ntnu.realfagskjelleren.rfid.settings.Settings;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Implementation of DBHandler for MySQL.
 *
 * @author Håvard Slettvold
 */
public class MySQLDBHandler implements DBHandler {

    private static Logger logger = LogManager.getLogger(MySQLDBHandler.class.getName());

    private Settings settings;

    private final String LOG_QS = "INSERT INTO log (message, date) VALUES (?, NOW());";

    public MySQLDBHandler(Settings settings) {
        this.settings = settings;
    }

    public boolean testConnection() {
        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT VERSION()")) {

            if (rs.next()) {
                logger.debug("MySQL server running version: " + rs.getString(1));
                return true;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage());
        }

        return false;
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

    /**
     * Finds the current version from the database.
     *
     * @return Version object with information about version of the database
     */
    @Override
    public Version getVersion() {
        String GET_VERSION_QS = "SELECT * FROM `version` ORDER BY id DESC LIMIT 1;";
        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(GET_VERSION_QS)){

            if (rs.next()) {
                return new Version(rs.getString("version"), rs.getTimestamp("executed_on"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Inserts a version entry into the database.
     *
     * @param version Version to be set
     * @return True if version was set
     */
    @Override
    public boolean setVersion(String version) {
        String SET_VERSION_QS = "INSERT INTO version (version) VALUES (?);";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(SET_VERSION_QS)) {

            ps.setString(1, version);
            ps.executeUpdate();

            return true;

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Makes a connection to the database.
     *
     * @return Connection to the database
     * @throws SQLException
     */
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
                String CREATE_USER_QS = "INSERT INTO user (credit, rfid, ecc, is_staff, created) VALUES (0, ?, ?, 0, NOW());";
                try (PreparedStatement ps = con.prepareStatement(CREATE_USER_QS)) {
                    ps.setString(1, rfid);
                    ps.setInt(2, makeECC());
                    ps.executeUpdate();
                }
            }

            String GET_USER_BY_RFID_QS = "SELECT * FROM user WHERE rfid = ?;";
            try (PreparedStatement ps = con.prepareStatement(GET_USER_BY_RFID_QS)) {
                ps.setString(1, rfid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        User user = new User(rs.getInt("id"), rs.getString("rfid"), rs.getBoolean("is_staff"), rs.getInt("credit"), rs.getTimestamp("created"), rs.getTimestamp("last_used"));
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
        String GET_USER_BY_ECC_QS = "SELECT * FROM user WHERE ecc = ?;";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(GET_USER_BY_ECC_QS)) {

            ps.setInt(1, ecc);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = new User(
                            rs.getInt("id"),
                            rs.getString("rfid"),
                            rs.getBoolean("is_staff"),
                            rs.getInt("credit"),
                            rs.getTimestamp("created"),
                            rs.getTimestamp("last_used")
                    );
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

    /**
     * This will override the RFID registered on a user. Meant to be used when users
     * have ro recover their account by ECC, or if they have both cards and wish to switch.
     *
     * @param user_id
     * @param rfid
     * @throws SQLException
     */
    @Override
    public void update_user_rfid(int user_id, String rfid) throws SQLException {
        String UPDATE_USER_RFID_QS = "UPDATE user SET rfid = ? WHERE id = ?;";
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
        String EXISTS_RFID_QS = "SELECT COUNT(*) FROM user WHERE rfid = ?;";
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
        String EXISTS_ECC_QS = "SELECT COUNT(*) FROM user WHERE ecc = ?;";
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

    /**
     * Deposits money into a user's account.
     *
     * @param rfid RFID of the user
     * @param value value to be inserted
     * @throws SQLException
     */
    @Override
    public void deposit(String rfid, int value) throws SQLException {
        String DEPOSIT_QS = "UPDATE user SET credit = credit+? WHERE rfid = ?;";
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

    /**
     * Deducts money from a user's account. This means withdrawing.
     *
     * @param rfid RFID of the user
     * @param value value to be deducted
     * @throws SQLException
     */
    @Override
    public void deduct(String rfid, int value) throws SQLException {
        String DEDUCT_QS = "UPDATE user SET credit = credit-? WHERE rfid = ?;";
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

    /**
     * This command will fetch all users stored in the database ordered by when they
     * were last used.
     *
     * @return List of users
     * @throws SQLException
     */
    @Override
    public List<User> getAllUsers() throws SQLException {
        List<User> users = new ArrayList<>();

        String GET_ALL_USERS_QS = "SELECT * FROM user ORDER BY last_used;";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(GET_ALL_USERS_QS)) {

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    User user = new User(
                            rs.getInt("id"),
                            rs.getString("rfid"),
                            rs.getBoolean("is_staff"),
                            rs.getInt("credit"),
                            rs.getTimestamp("created"),
                            rs.getTimestamp("last_used")
                    );
                    users.add(user);
                }
            }

        } catch (SQLException ex) {
            logger.error("Failed to retrieve all users.");
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        return users;
    }

    /**
     * Get a count of all users stored in the database.
     *
     * @return Integer amount of all users
     * @throws SQLException
     */
    public int getUserCount() throws SQLException {
        String USER_COUNT_QS = "SELECT COUNT(*) FROM user;";
        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(USER_COUNT_QS)) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException ex) {
            logger.error("Failed to obtain user count.");
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        return -1;
    }

    /**
     * Returns the total value of the combined credit of all users.
     *
     * @return Value stored in the system
     * @throws SQLException
     */
    public int getTotalValue() throws SQLException {
        String TOTAL_VALUE_QS = "SELECT ABS(SUM(credit)) AS total FROM user;";
        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(TOTAL_VALUE_QS)) {

            if (rs.next()) {
                return rs.getInt("total");
            }

        } catch (SQLException ex) {
            logger.error("Failed to obtain total value.");
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        return -1;
    }

    /**
     * Fetches the last 'amount' transactions from the database.
     *
     * @param amount The amount fo transactions to return
     * @return List of Transaction objects
     * @throws SQLException
     */
    @Override
    public List<Transaction> getTransactions(int amount) throws SQLException {
        List <Transaction> transactions = new ArrayList<>();

        String GET_TRANSACTIONS_QS = "SELECT * " +
                                     "FROM transaction AS t " +
                                     "INNER JOIN user AS u " +
                                     "ON t.user_id = u.id " +
                                     "ORDER BY t.id DESC LIMIT ?;";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(GET_TRANSACTIONS_QS)) {

            ps.setInt(1, amount);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Transaction tr = new Transaction(
                            rs.getInt("user_id"),
                            rs.getString("rfid"),
                            rs.getInt("value"),
                            rs.getInt("new_balance"),
                            rs.getBoolean("is_deposit"),
                            rs.getTimestamp("date")
                    );
                    transactions.add(tr);
                }
            }
        } catch (SQLException ex) {
            logger.error("Could not retrieve transactions.");
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        return transactions;
    }

    /**
     * Retrieves transactions from the database from a specified time window.
     *
     * @param hours Hours back in time to look
     * @return List of Transaction objects
     * @throws SQLException
     */
    @Override
    public List<Transaction> getTransactionsFromLastHours(int hours) throws SQLException {
        List <Transaction> transactions = new ArrayList<>();

        String GET_TRANSACTIONS_FROM_LAST_HOURS_QS = "SELECT * " +
                                     "FROM transaction AS t " +
                                     "INNER JOIN user AS u " +
                                     "ON t.user_id = u.id " +
                                     "WHERE t.date > DATE_SUB(NOW(), INTERVAL ? HOUR);";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(GET_TRANSACTIONS_FROM_LAST_HOURS_QS)) {

            ps.setInt(1, hours);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Transaction tr = new Transaction(
                            rs.getInt("user_id"),
                            rs.getString("rfid"),
                            rs.getInt("value"),
                            rs.getInt("new_balance"),
                            rs.getBoolean("is_deposit"),
                            rs.getTimestamp("date")
                    );
                    transactions.add(tr);
                }
            }
        } catch (SQLException ex) {
            logger.error("Could not retrieve transactions.");
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        return transactions;
    }

    /**
     * Fetches the last 'amount' transactions from the database which matches the supplied user.
     *
     * @param user_id ID of the user to filter on
     * @param amount The amount fo transactions to return
     * @return List of Transaction objects
     * @throws SQLException
     */
    @Override
    public List<Transaction> getTransactions(int user_id, int amount) throws SQLException {
        List <Transaction> transactions = new ArrayList<>();

        String GET_TRANSACTIONS_BY_USER_QS = "SELECT * FROM transaction AS t INNER JOIN user AS u ON t.user_id = u.id WHERE u.id = ? ORDER BY t.id DESC LIMIT ?;";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(GET_TRANSACTIONS_BY_USER_QS)) {

            ps.setInt(1, user_id);
            ps.setInt(2, amount);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Transaction tr = new Transaction(
                            rs.getInt("user_id"),
                            rs.getString("rfid"),
                            rs.getInt("value"),
                            rs.getInt("new_balance"),
                            rs.getBoolean("is_deposit"),
                            rs.getTimestamp("date")
                    );
                    transactions.add(tr);
                }
            }


        } catch (SQLException ex) {
            logger.error("Could not retrieve transactions.");
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        return transactions;
    }

    /**
     * Fetches the sales for a specified date.
     *
     * @param date Date to look up
     * @return String in the format; "date|sales"
     * @throws SQLException
     */
    @Override
    public String getSalesForDate(String date) throws SQLException {
        String SALES_FOR_DATE = "SELECT SUM(value) AS sales, DATE_FORMAT(date, '%W %d-%m-%Y') AS date " +
                                "FROM `transaction` " +
                                "WHERE is_deposit != 1 " +
                                "AND value < 1000 " +
                                "AND DATE_FORMAT(DATE(date - INTERVAL 9 hour), '%Y-%m-%d') = ?;";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(SALES_FOR_DATE)) {

            ps.setString(1, date);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (rs.getString("date") == null) return null;
                    return rs.getString("date") +"|"+ rs.getInt("sales");
                }
            }


        } catch (SQLException ex) {
            logger.error("Could not retrieve transactions.");
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        return null;
    }

    /**
     * Returns all days the system has been used and how much the sales were that day.
     *
     * @return List of Strings in the format: "# | date | sales"
     * @throws SQLException
     */
    @Override
    public List<String> topDays() throws SQLException {
        List<String> topDays = new ArrayList<>();

        String TOP_DAYS_QS = "SELECT SUM(value) AS sales, DATE_FORMAT(date, '%W %d-%m-%Y') AS date " +
                             "FROM `transaction` " +
                             "WHERE is_deposit != 1 AND value < 1000 " +
                             "GROUP BY DATE(date - INTERVAL 9 hour) " +
                             "ORDER BY sales DESC;";
        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(TOP_DAYS_QS)) {

            int i = 0;
            while (rs.next()) {
                i++;
                topDays.add(i +"|"+ rs.getString("date") +"|"+ rs.getInt("sales"));
            }
        } catch (SQLException ex) {
            logger.error("Could not generate top days.");
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        return topDays;
    }

    /**
     * Fetches the total amount spent by a user.
     *
     * @param rfid RFID to look up
     * @return sum of all purchases
     * @throws SQLException
     */
    @Override
    public int totalSpendings(String rfid) throws SQLException {
        String TOP_DAYS_QS = "SELECT SUM( t.value ) AS spent " +
                "FROM transaction AS t " +
                "INNER JOIN user AS u ON t.user_id = u.id " +
                "WHERE t.is_deposit !=1 AND t.value <1000 AND u.rfid = ?;";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(TOP_DAYS_QS)) {

            ps.setString(1, rfid);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("spent");
                }
            }
        } catch (SQLException ex) {
            logger.error(String.format("Could not fetch total spendings for %s.", rfid));
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        return -1;
    }

    /**
     * Makes a list of the top ten spenders through all time.
     *
     * @return List of strings: "RFID|Amount"
     * @throws SQLException
     */
    @Override
    public List<String> getTopTen() throws SQLException {
        List<String> topTen = new ArrayList<>();

        String TOP_TEN_QS = "SELECT ABS(SUM(t.value)) AS spent, u.rfid " +
                            "FROM transaction AS t " +
                            "INNER JOIN user AS u " +
                            "ON t.user_id = u.id " +
                            "WHERE t.is_deposit != 1 AND t.value < 1000 " +
                            "GROUP BY u.rfid " +
                            "ORDER BY spent DESC " +
                            "LIMIT 10;";
        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(TOP_TEN_QS)) {

            while (rs.next()) {
                topTen.add(rs.getString("rfid") +"|"+ rs.getInt("spent"));
            }

        } catch (SQLException ex) {
            logger.error("Could not generate top ten.");
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        return topTen;
    }

    /**
     * Makes a list of the top ten spenders over the last hours.
     *
     * @param hours Hours to include
     * @return List of strings: "RFID|Amount"
     * @throws SQLException
     */
    @Override
    public List<String> getTopTenFromLastHours(int hours) throws SQLException {
        List<String> topTen = new ArrayList<>();

        String TOP_TEN_QS = "SELECT ABS(SUM(t.value)) AS spent, u.rfid " +
                            "FROM transaction AS t " +
                            "INNER JOIN user AS u " +
                            "ON t.user_id = u.id " +
                            "WHERE t.is_deposit != 1 AND t.date > DATE_SUB(NOW(), INTERVAL ? HOUR) " +
                            "GROUP BY u.rfid " +
                            "ORDER BY spent DESC " +
                            "LIMIT 10;";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(TOP_TEN_QS)) {

            ps.setInt(1, hours);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    topTen.add(rs.getString("rfid") +"|"+ rs.getInt("spent"));
                }
            }

        } catch (SQLException ex) {
            logger.error("Could not generate top ten.");
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        return topTen;
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
        String TRANSACTION_QS = "INSERT INTO transaction (user_id, value, is_deposit, new_balance) VALUES (?, ?, ?, ?);";
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

    /**
     * This method is only supposed to be used with the importing of older SQL databases
     * to allow the timestamp to be set.
     *
     * @param rfid RFID to create a user for
     * @param created Timestamp it was created
     * @return True if a user was created
     * @throws SQLException
     */
    @Override
    public boolean create_user_from_previous_db(String rfid, int credit, Timestamp created) throws SQLException {
        if (!rfid_exists(rfid)) {
            String CREATE_USER_QS = "INSERT INTO user (credit, rfid, ecc, is_staff, created) VALUES (?, ?, ?, 0, ?);";
            try (Connection con = getConnection();
                 PreparedStatement ps = con.prepareStatement(CREATE_USER_QS)) {

                ps.setInt(1, credit);
                ps.setString(2, rfid);
                ps.setInt(3, makeECC());
                ps.setTimestamp(4, created);
                ps.executeUpdate();

                return true;
            } catch (SQLException ex) {
                logger.error(String.format("Failed to create user for RFID '%s'.", rfid));
                logger.error(ex.getMessage(), ex);
                throw ex;
            }
        }
        return false;
    }

    /**
     * This method is only supposed to be used with the importing of older SQL databases
     * to allow the timestamp to be set.
     *
     * @param user_id ID of {@link User} for this transaction
     * @param value Amount of money
     * @param is_deposit True if money was deposited
     * @param new_balance The new balance for {@link User}
     * @param timestamp Timestamp of the transaction
     * @throws SQLException
     */
    @Override
    public void transaction(int user_id, int value, boolean is_deposit, int new_balance, Timestamp timestamp) throws SQLException {
        String TRANSACTION_QS = "INSERT INTO transaction (user_id, value, is_deposit, new_balance, date) VALUES (?, ?, ?, ?, ?);";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(TRANSACTION_QS)) {

            ps.setInt(1, user_id);
            ps.setInt(2, value);
            ps.setBoolean(3, is_deposit);
            ps.setInt(4, new_balance);
            ps.setTimestamp(5, timestamp);
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
            // ECC is now a random number between 100000-999999
            ecc = rand.nextInt((999999 - 100000) + 1) + 100000;
        } while (ecc_exists(ecc));
        return ecc;
    }

}