package me.planetguy.stool;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;

import java.sql.*;
import java.util.*;

public class SqlLogger {

    private static Connection conn = null;
    private static PreparedStatement addEvent;
    private static PreparedStatement declareWinner;
    private static PreparedStatement addMatch;
    private static PreparedStatement searchNearby;

    public static HashSet<String> knownTeam1 = new HashSet<>();
    public static HashSet<String> knownTeam2 = new HashSet<>();

    private static Set<String> pausingUsers = new HashSet<String>();

    private static long matchStart = 0;

    private static long currentPausePeriodStart = -1;
    private static long matchTimeSkipped = 0;

    public static String mapName = "";


    private static void resetMatchData() {
        knownTeam1 = new HashSet<>();
        knownTeam2 = new HashSet<>();

        pausingUsers = new HashSet<String>();

        matchStart = 0;

        currentPausePeriodStart = -1;
        matchTimeSkipped = 0;

        mapName = "";
    }

    public static void setupSql() {
        try {
            // db parameters
            String url = "jdbc:sqlite:stool.db";
            // create a connection to the database
            conn = DriverManager.getConnection(url);

            System.out.println("Connection to SQLite has been established.");


            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS matches (\n"
                    + "	starttime long PRIMARY KEY,\n"
                    + "	mapid INTEGER,\n"
                    //Players. We have support for a 10v10 here, though that's very ambitious.
                    + "	t1p1 text,\n"
                    + "	t1p2 text,\n"
                    + "	t1p3 text,\n"
                    + "	t1p4 text,\n"
                    + "	t1p5 text,\n"
                    + "	t1p6 text,\n"
                    + "	t1p7 text,\n"
                    + "	t1p8 text,\n"
                    + "	t1p9 text,\n"
                    + "	t1p10 text,\n"
                    + "	t2p1 text,\n"
                    + "	t2p2 text,\n"
                    + "	t2p3 text,\n"
                    + "	t2p4 text,\n"
                    + "	t2p5 text,\n"
                    + "	t2p6 text,\n"
                    + "	t2p7 text,\n"
                    + "	t2p8 text,\n"
                    + "	t2p9 text,\n"
                    + "	t2p10 text,\n"
                    + "	winner INTEGER\n" // boolean
                    + ");");

            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS events (\n"
                            + " match LONG,\n"
                            + "	realtime long PRIMARY KEY,\n"
                            + "	gametime long,\n"
                            + "	eventtype text NOT NULL,\n"
                            + "	player text,\n"
                            + "	x REAL,\n"
                            + "	y REAL,\n"
                            + "	z REAL,\n"
                            + "	extra1 text,\n"
                            + "	extra2 text,\n"
                            + "	extra3 text,\n"
                            + " FOREIGN KEY(match) REFERENCES matches(starttime)"
                            + ");" +
                            "CREATE INDEX time_idx ON events (realtime IDX);" +
                            "CREATE INDEX type_idx ON events (eventtype IDX);" +
                            "CREATE INDEX plyr_idx ON events (player IDX);");

            addMatch = conn.prepareStatement(
                    "INSERT INTO matches VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '')");

            declareWinner = conn.prepareStatement(
                    "UPDATE matches SET winner = ? WHERE starttime = ?");

            addEvent = conn.prepareStatement(
                    "INSERT INTO events VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS `eventsOrderedIdx` ON `events` (\n" +
                    "\t`match`\tASC,\n" +
                    "\t`realtime`\tASC,\n" +
                    "`player`,\n" +
                    "`eventtype`\n" +
                    ");");

            searchNearby = conn.prepareStatement("SELECT count(*) FROM events " +
                    "WHERE eventtype = ? " +
                    "AND x > ? " +
                    "AND x < ? " +
                    "AND y > ? " +
                    "AND y < ? " +
                    "AND z > ? " +
                    "AND z < ?;");

        } catch (SQLException e) {
            throw (new RuntimeException(e));
        }
    }

    static long matchTime() {
        return System.currentTimeMillis() - matchStart - matchTimeSkipped;
    }

    public static void assignPlayerToTeam(String player, int team, String mapName_) {
        if (mapName_ != null)
            mapName = mapName_;
        if (team == 1) {
            knownTeam1.add(player);
        } else if (team == 2) {
            knownTeam2.add(player);
        }
    }

    public static String startMatch(List<String> users) {
        if (matchStart == 0) {
            try {
                //if(knownTeam1.size()==0 || knownTeam2.size()==0)
                //    return "\u00A76Set up a match first!";
                matchStart = System.currentTimeMillis();
                addMatch.setLong(1, matchStart);
                addMatch.setString(2, mapName);
                int i = 3;
                List<String> team1Sorted = new ArrayList<String>(knownTeam1);
                team1Sorted.sort(new Comparator<String>() {
                    @Override
                    public int compare(String s, String t1) {
                        return s.compareTo(t1);
                    }
                });
                for (String uname : team1Sorted) {
                    addMatch.setString(i++, uname);
                }
                while (i < 12) {
                    addMatch.setString(i, "");
                    i++;
                }
                List<String> team2Sorted = new ArrayList<String>(knownTeam2);
                team1Sorted.sort(new Comparator<String>() {
                    @Override
                    public int compare(String s, String t1) {
                        return s.compareTo(t1);
                    }
                });
                for (String uname : team2Sorted) {
                    addMatch.setString(i++, uname);
                }
                while (i <= 22)
                    addMatch.setString(i++, "");
                addMatch.execute();
                return "\u00A76Started match: " + String.join(", ", team1Sorted) + " VS " + String.join(", ", team2Sorted);
            } catch (SQLException e) {
                e.printStackTrace();
                matchStart = 0;
                return "\u00A76Did not start match?!";
            }
        }
        return "\u00A76Match is in progress?";
    }

    public static boolean pause(EntityPlayer username) {
        pausingUsers.add(username.getDisplayName());
        addEvent(username, "pause", Boolean.toString(currentPausePeriodStart == -1));
        if (currentPausePeriodStart == -1) {
            currentPausePeriodStart = System.currentTimeMillis();
        }
        return true;
    }

    public static boolean resume(EntityPlayer username) {
        pausingUsers.remove(username.getDisplayName());
        addEvent(username, "resume", Boolean.toString(pausingUsers.size() == 0));
        if (pausingUsers.size() == 0) {
            //Account for skipped time
            long now = System.currentTimeMillis();
            matchTimeSkipped += (now - currentPausePeriodStart);
            //Unpause
            currentPausePeriodStart = -1;
        }
        return true;
    }

    public static boolean forceResume(EntityPlayer username) {
        pausingUsers.clear();
        addEvent(username, "fresume", Boolean.toString(pausingUsers.size() == 0));
        //Account for skipped time
        long now = System.currentTimeMillis();
        matchTimeSkipped += (now - currentPausePeriodStart);
        //Unpause
        currentPausePeriodStart = -1;
        return true;
    }

    public static void addEvent(EntityPlayer user, String eventType, String... extras) {
        try {
            if (matchStart == 0)
                return;
            //Player is not actually in the game
            if(!(knownTeam1.contains(user.getDisplayName()) || knownTeam2.contains(user.getDisplayName())))
                return;
            addEvent.setLong(1, matchStart);
            addEvent.setLong(2, System.currentTimeMillis());
            addEvent.setLong(3, matchTime());
            addEvent.setString(4, eventType);
            addEvent.setString(5, user.getDisplayName());
            addEvent.setDouble(6, user.posX);
            addEvent.setDouble(7, user.posY);
            addEvent.setDouble(8, user.posZ);
            int i;
            for (i = 0; i < 3 && i < extras.length; i++) {
                addEvent.setString(i + 9, extras[i]);
            }
            for (; i < 3; i++) {
                addEvent.setString(i + 9, "");
            }
            addEvent.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean win(EntityPlayer player) {
        if (knownTeam1.contains(player.getDisplayName())) {
            return endGame(player, 1);
        } else if (knownTeam2.contains(player.getDisplayName())) {
            return endGame(player, 2);
        } else {
            return false;
        }
    }

    public static boolean lose(EntityPlayer player) {
        if (knownTeam1.contains(player.getDisplayName())) {
            return endGame(player, 2);
        } else if (knownTeam2.contains(player.getDisplayName())) {
            return endGame(player, 1);
        } else {
            return false;
        }
    }


    private static boolean endGame(EntityPlayer player, int winner) {
        try {
            addEvent(player, "endgame", "" + winner);
            declareWinner.setInt(1, winner);
            declareWinner.execute();
            Stool.broadcastMessage("\u00A76The winners are: " + String.join(",", winner == 1 ? knownTeam1 : knownTeam2));
            resetMatchData();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }


    public static boolean isInGame() {
        return matchStart != 0;
    }

    public static String getCurrentGameGuess() {
        return "\u00A76Current game: \n"
                + "\u00A76Map: " + mapName + "\n"
                + "\u00A76Team 1: " + String.join(", ", knownTeam1) + "\n"
                + "\u00A76Team 2: " + String.join(", ", knownTeam2);
    }

    public static int countEventsInBounds(String type, AxisAlignedBB aabb) {
        try {
            searchNearby.setString(1, type);
            searchNearby.setDouble(2, aabb.minX);
            searchNearby.setDouble(3, aabb.maxX);
            searchNearby.setDouble(4, aabb.minY);
            searchNearby.setDouble(5, aabb.maxY);
            searchNearby.setDouble(6, aabb.minZ);
            searchNearby.setDouble(7, aabb.maxZ);
            ResultSet rs = searchNearby.executeQuery();
            System.out.println(rs);
            return rs.getInt(1);
        } catch (SQLException e) {

        }
        return -1;
    }
}
