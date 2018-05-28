package me.planetguy.stool;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.Sys;

import java.sql.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class SqlLogger {

    public static SqlLogger ACTIVE_LOGGER=new SqlLogger();

    public final int MAX_PER_TEAM=10;

    private Connection conn = null;
    private PreparedStatement addEvent;
    private PreparedStatement declareWinner;
    private PreparedStatement addMatch;
    private PreparedStatement searchNearby;

    private PreparedStatement[] setTeam1=new PreparedStatement[MAX_PER_TEAM];
    private PreparedStatement[] setTeam2=new PreparedStatement[MAX_PER_TEAM];

    private HashSet<String> knownTeam1 = new HashSet<>();
    private HashSet<String> knownTeam2 = new HashSet<>();

    private Set<String> pausingUsers = new HashSet<>();

    private long matchStart = 0;

    private long currentPausePeriodStart = -1;
    private long matchTimeSkipped = 0;

    private String mapName = "";

    private Thread eventUploader;
    private final BlockingQueue<StoolEvent> queue=new LinkedBlockingDeque<>();

    public SqlLogger() {
        eventUploader=new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    try {
                        StoolEvent ev=queue.take();
                        synchronized(this){
                            addEventToDB(ev);
                        }
                    } catch (InterruptedException e) {

                    }
                }
            }
        }, Constants.modID+"_eventUploader");
        eventUploader.setPriority(Thread.currentThread().getPriority()-1);
        eventUploader.start();
    }

    private synchronized void resetMatchData() {
        knownTeam1 = new HashSet<>();
        knownTeam2 = new HashSet<>();

        pausingUsers = new HashSet<String>();

        matchStart = 0;

        currentPausePeriodStart = -1;
        matchTimeSkipped = 0;

        mapName = "";
    }

    public synchronized void setupSql() {
        try {
            // db parameters
            String url = "jdbc:sqlite:stool.db";
            // create a connection to the database
            conn = DriverManager.getConnection(url);


            conn.setAutoCommit(false);

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
                            + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + " match LONG,\n"
                            + "	realtime long,\n"
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
                    "INSERT INTO matches" +
                            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '')");

            declareWinner = conn.prepareStatement(
                    "UPDATE matches SET winner = ? WHERE starttime = ?");


            for(int i=1; i<=10; i++){
                 setTeam1[i-1] = conn.prepareStatement(
                        "UPDATE matches SET t1p"+i+" = ? WHERE starttime = ?");
                 setTeam2[i-1] = conn.prepareStatement(
                        "UPDATE matches SET t2p"+i+" = ? WHERE starttime = ?");
            }

            addEvent = conn.prepareStatement(
                    "INSERT INTO events" +
                            " (match, realtime, gametime, eventtype, player, x, y, z, extra1, extra2, extra3) " +
                            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

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

    long matchTime(long timeSample) {
        return timeSample - matchStart - matchTimeSkipped;
    }

    public synchronized void assignPlayerToTeam(String player, int team, String mapName_) {
        if (mapName_ != null)
            mapName = mapName_;
        if (team == 1) {
            knownTeam1.add(player);
        } else if (team == 2) {
            knownTeam2.add(player);
        }
    }

    public synchronized String startMatch(List<String> users) {
        if(Stool.IS_DB_READ_ONLY)
            return "\u00A76This is edit!";
        if (matchStart == 0) {
            try {
                if(knownTeam1.size()==0 && knownTeam2.size()==0)
                    return "\u00A76Cannot setup match with no players!";
                if(mapName.equals(""))
                    return "\u00A76Cannot setup match with no map!";
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

    public synchronized boolean pause(EntityPlayer username) {
        pausingUsers.add(username.getDisplayName());
        addEvent(username, "pause", Boolean.toString(currentPausePeriodStart == -1));
        if (currentPausePeriodStart == -1) {
            currentPausePeriodStart = System.currentTimeMillis();
        }
        return true;
    }

    public synchronized boolean resume(EntityPlayer username) {
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

    public synchronized boolean forceResume(EntityPlayer username) {
        pausingUsers.clear();
        addEvent(username, "fresume", Boolean.toString(pausingUsers.size() == 0));
        //Account for skipped time
        long now = System.currentTimeMillis();
        matchTimeSkipped += (now - currentPausePeriodStart);
        //Unpause
        currentPausePeriodStart = -1;
        return true;
    }

    public synchronized void addEvent(EntityPlayer user, String eventType, String... extras) {
        if(matchStart==0 || user.worldObj.isRemote)
            return;
        long now=System.currentTimeMillis();
        queue.add(new StoolEvent(now, matchTime(now), user, eventType, extras));
    }


    private synchronized void addEventToDB(StoolEvent e) {
        if (Stool.IS_DB_READ_ONLY)
            return;
        try {
            System.out.println("Ev "+e.getEventType()+" "+e.getDisplayName()+String.join(" ", e.getExtras()));
            //Player is not actually in the game
            if (!(knownTeam1.contains(e.getDisplayName()) || knownTeam2.contains(e.getDisplayName())))
                return;
            addEvent.setLong(1, matchStart);
            addEvent.setLong(2, e.realTime);
            addEvent.setLong(3, e.matchTime);
            addEvent.setString(4, e.getEventType());
            addEvent.setString(5, e.getDisplayName());
            addEvent.setDouble(6, e.posX);
            addEvent.setDouble(7, e.posY);
            addEvent.setDouble(8, e.posZ);
            int i;
            for (i = 0; i < 3 && i < e.getExtras().length; i++) {
                addEvent.setString(i + 9, e.getExtras()[i]);
            }
            for (; i < 3; i++) {
                addEvent.setString(i + 9, "");
            }
            addEvent.execute();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public synchronized boolean win(EntityPlayer player) {
        if (knownTeam1.contains(player.getDisplayName())) {
            return endGame(player, 1);
        } else if (knownTeam2.contains(player.getDisplayName())) {
            return endGame(player, 2);
        } else {
            return false;
        }
    }

    public synchronized boolean lose(EntityPlayer player) {
        if (knownTeam1.contains(player.getDisplayName())) {
            return endGame(player, 2);
        } else if (knownTeam2.contains(player.getDisplayName())) {
            return endGame(player, 1);
        } else {
            return false;
        }
    }

    public void stalemate(EntityPlayer player){
        endGame(player, -2);
    }

    public void invalid(EntityPlayer player){
        endGame(player, -1);
    }


    private synchronized boolean endGame(EntityPlayer player, int winner) {
        if(Stool.IS_DB_READ_ONLY)
            return false;
        try {
            addEvent(player, "endgame", "" + winner);
            declareWinner.setInt(1, winner);
            declareWinner.setLong(2, matchStart);
            declareWinner.execute();
            conn.commit();
            if(winner==1 || winner==2)
                Stool.broadcastMessage("\u00A76The winners are: " + String.join(",", winner == 1 ? knownTeam1 : knownTeam2));
            else if(winner==-2)
                Stool.broadcastMessage("\u00A76Match is a stalemate!");
            else if(winner==-1)
                Stool.broadcastMessage("\u00A76Match is invalid!");
            resetMatchData();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }


    public boolean isInGame() {
        return matchStart != 0;
    }

    public synchronized String getCurrentGameGuess() {
        String s="\u00A76Current game: \n"
                + "\u00A76Map: " + mapName + "\n"
                + "\u00A76Team 1: " + String.join(", ", knownTeam1) + "\n"
                + "\u00A76Team 2: " + String.join(", ", knownTeam2) + "\n";
        if (matchStart != 0) {
            s+="\u00A76Elapsed time:"+matchTime(System.currentTimeMillis())/1000+" seconds\n";
        }
        return s;
    }

    public synchronized int countEventsInBounds(String type, AxisAlignedBB aabb) {
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

    public synchronized CharSequence adjustTeam(int i, String[] names, EntityPlayer ics) throws SQLException {
        Set<String> team=(i==1 ? knownTeam1 : knownTeam2);
        Set<String> otherTeam=(i==1 ? knownTeam2 : knownTeam1);

        String oldTeamString=String.join(", ", team);
        team.clear();
        team.addAll(Arrays.asList(names));

        //Make sure nobody is on both teams
        otherTeam.removeAll(team);

        String newTeamString=String.join(", ", team);

        addEvent((EntityPlayer) ics, "adjustTeam",
                Integer.toString(i),
                oldTeamString,
                newTeamString);

        Iterator<String> t1Iterator=knownTeam1.iterator();
        Iterator<String> t2Iterator=knownTeam2.iterator();

        for(int j=0; j<setTeam1.length; j++){
            setTeam1[j].setString(1, t1Iterator.hasNext() ? t1Iterator.next() : "");
            setTeam1[j].setLong(2, matchStart);
            setTeam1[j].execute();

            setTeam2[j].setString(1, t2Iterator.hasNext() ? t2Iterator.next() : "");
            setTeam2[j].setLong(2, matchStart);
            setTeam2[j].execute();
        }

        return newTeamString;
    }

    public synchronized void setMap(String map) {
        mapName = map;
    }

    public void shutdown() {
        try {
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
