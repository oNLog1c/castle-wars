package me.nologic.castlewars.battleground.util;

import lombok.Getter;
import lombok.SneakyThrows;
import me.nologic.castlewars.CastleWars;
import me.nologic.castlewars.battleground.Battleground;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class BattlegroundDataDriver {

    private final CastleWars plugin = CastleWars.getInstance();

    @SneakyThrows
    public ResultSet executeQuery(final String sql, final boolean next) {
        final ResultSet result = this.connection.createStatement().executeQuery(sql);
        if (next) result.next();
        return result;
    }

    @SneakyThrows
    public ResultSet executeQuery(final String sql, final Object... args) {
        final PreparedStatement statement = this.connection.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) {
            statement.setObject(i + 1, args[i]);
        }
        return statement.executeQuery();
    }

    @SneakyThrows
    public BattlegroundDataDriver executeUpdate(String sql, Object... args) {
        PreparedStatement statement = this.connection.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) {
            statement.setObject(i + 1, args[i]);
        }
        statement.executeUpdate();
        return this;
    }

    @Getter
    private Connection connection; @SneakyThrows
    public BattlegroundDataDriver connect(final String battlegroundName) {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/battlegrounds/" + battlegroundName + ".db");
        this.checksum();
        return this;
    }

    public BattlegroundDataDriver connect(Battleground battleground) {
        return this.connect(battleground.getBattlegroundName());
    }

    private void checksum() {
        for (DatabaseTableHelper table : DatabaseTableHelper.values()) {
            this.executeUpdate(table.getCreateStatement());
        }
    }

    @SneakyThrows
    public void closeConnection() {
        this.connection.close();
    }

}