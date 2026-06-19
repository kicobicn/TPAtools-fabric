package com.kicobicn.TPATools.database;

import com.kicobicn.TPATools.config.ModConfigs;
import com.mysql.cj.jdbc.Driver;

import java.sql.*;
import java.util.Properties;

public class DatabaseManager {
    private static Connection connection;
    private static String DATABASE_TYPE = "json"; // 移除final修饰符
    
    // MySQL配置
    private static String host;
    private static int port;
    private static String database;
    private static String username;
    private static String password;

    // 初始化数据库连接
    public static void initialize() {
        // 从配置文件读取数据库设置
        DATABASE_TYPE = ModConfigs.DATABASE_TYPE.get();
        
        if (DATABASE_TYPE.equals("mysql")) {
            host = ModConfigs.MYSQL_HOST.get();
            port = ModConfigs.MYSQL_PORT.getAsInt();
            database = ModConfigs.MYSQL_DATABASE.get();
            username = ModConfigs.MYSQL_USERNAME.get();
            password = ModConfigs.MYSQL_PASSWORD.get();
            
            try {
                DriverManager.registerDriver(new Driver());
                String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC", 
                                          host, port, database);
                Properties props = new Properties();
                props.setProperty("user", username);
                props.setProperty("password", password);
                
                connection = DriverManager.getConnection(url, props);
                createTables();
                ModConfigs.DebugLog.info("MySQL connection established");
            } catch (SQLException e) {
                ModConfigs.DebugLog.error("Failed to connect to MySQL database: {}", e.getMessage());
                // 回退到JSON模式
                DATABASE_TYPE = "json";
            }
        }
    }
    
    // 创建数据库表
    private static void createTables() throws SQLException {
        // 创建命令权限表
        String createPermissionsTable = """
            CREATE TABLE IF NOT EXISTS command_permissions (
                command VARCHAR(50) PRIMARY KEY,
                need_op BOOLEAN NOT NULL
            )
        """;
        
        // 创建免打扰状态表
        String createTogglesTable = """
            CREATE TABLE IF NOT EXISTS toggle_states (
                player_uuid VARCHAR(36) PRIMARY KEY,
                enabled BOOLEAN NOT NULL
            )
        """;
        
        // 创建锁定玩家表
        String createLockedPlayersTable = """
            CREATE TABLE IF NOT EXISTS locked_players (
                player_uuid VARCHAR(36) NOT NULL,
                locked_uuid VARCHAR(36) NOT NULL,
                PRIMARY KEY (player_uuid, locked_uuid)
            )
        """;
        
        // 创建玩家家园表
        String createHomesTable = """
            CREATE TABLE IF NOT EXISTS player_homes (
                player_uuid VARCHAR(36) NOT NULL,
                home_name VARCHAR(50) NOT NULL,
                dimension VARCHAR(100) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                x_rot FLOAT NOT NULL,
                y_rot FLOAT NOT NULL,
                PRIMARY KEY (player_uuid, home_name)
            )
        """;
        
        // 创建家园分享表
        String createHomeSharesTable = """
            CREATE TABLE IF NOT EXISTS home_shares (
                player_uuid VARCHAR(36) NOT NULL,
                home_name VARCHAR(50) NOT NULL,
                shared_with VARCHAR(36) NOT NULL,
                PRIMARY KEY (player_uuid, home_name, shared_with),
                FOREIGN KEY (player_uuid, home_name) REFERENCES player_homes(player_uuid, home_name)
            )
        """;
        
        // 创建公开家园表
        String createPublicHomesTable = """
            CREATE TABLE IF NOT EXISTS public_homes (
                player_uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(50) NOT NULL,
                home_name VARCHAR(50) NOT NULL,
                PRIMARY KEY (player_uuid, home_name),
                FOREIGN KEY (player_uuid, home_name) REFERENCES player_homes(player_uuid, home_name)
            )
        """;
        
        // 创建死亡位置表
        String createGravesTable = """
            CREATE TABLE IF NOT EXISTS player_graves (
                player_uuid VARCHAR(36) PRIMARY KEY,
                dimension VARCHAR(100) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                x_rot FLOAT NOT NULL,
                y_rot FLOAT NOT NULL
            )
        """;
        
        // 执行创建表语句
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createPermissionsTable);
            stmt.execute(createTogglesTable);
            stmt.execute(createLockedPlayersTable);
            stmt.execute(createHomesTable);
            stmt.execute(createHomeSharesTable);
            stmt.execute(createPublicHomesTable);
            stmt.execute(createGravesTable);
        }
    }
    
    // 获取数据库连接
    public static Connection getConnection() {
        return connection;
    }
    
    // 检查是否使用MySQL
    public static boolean isUsingMySQL() {
        return DATABASE_TYPE.equals("mysql") && connection != null;
    }
    
    // 关闭数据库连接
    public static void close() {
        if (connection != null) {
            try {
                connection.close();
                ModConfigs.DebugLog.info("MySQL connection closed");
            } catch (SQLException e) {
                ModConfigs.DebugLog.error("Failed to close MySQL connection: {}", e.getMessage());
            }
        }
    }
}