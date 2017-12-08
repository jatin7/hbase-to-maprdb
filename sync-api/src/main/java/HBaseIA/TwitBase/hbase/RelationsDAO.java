package HBaseIA.TwitBase.hbase;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import utils.Md5Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RelationsDAO {

    // md5(id_from)md5(id_to) -> 'f':id_to=name_to
    // md5(id_from)md5(id_to) -> 'f':'to'=id_to, 'f':'from'=id_from

    public static final byte[] FOLLOWS_TABLE_NAME_BYTES = Bytes.toBytes("/follows");
    public static final TableName FOLLOWS_TABLE_NAME = TableName.valueOf(FOLLOWS_TABLE_NAME_BYTES);
    public static final byte[] FOLLOWED_TABLE_NAME_BYTES = Bytes.toBytes("/followedBy");
    public static final TableName FOLLOWED_TABLE_NAME = TableName.valueOf(FOLLOWED_TABLE_NAME_BYTES);
    public static final byte[] RELATION_FAM = Bytes.toBytes("f");
    public static final byte[] FROM = Bytes.toBytes("from");
    public static final byte[] TO = Bytes.toBytes("to");

    private static final int KEY_WIDTH = 2 * Md5Utils.MD5_LENGTH;

    private Connection connection;

    public RelationsDAO(Connection connection) {
        this.connection = connection;
    }

    public static byte[] mkRowKey(String a) {
        byte[] ahash = Md5Utils.md5sum(a);
        byte[] rowkey = new byte[KEY_WIDTH];

        Bytes.putBytes(rowkey, 0, ahash, 0, ahash.length);
        return rowkey;
    }

    public static byte[] mkRowKey(String a, String b) {
        byte[] ahash = Md5Utils.md5sum(a);
        byte[] bhash = Md5Utils.md5sum(b);
        byte[] rowkey = new byte[KEY_WIDTH];

        int offset = 0;
        offset = Bytes.putBytes(rowkey, offset, ahash, 0, ahash.length);
        Bytes.putBytes(rowkey, offset, bhash, 0, bhash.length);
        return rowkey;
    }

    public static byte[][] splitRowkey(byte[] rowkey) {
        byte[][] result = new byte[2][];

        result[0] = Arrays.copyOfRange(rowkey, 0, Md5Utils.MD5_LENGTH);
        result[1] = Arrays.copyOfRange(rowkey, Md5Utils.MD5_LENGTH, KEY_WIDTH);
        return result;
    }

    public void addFollows(String fromId, String toId) throws IOException {
        addRelation(FOLLOWS_TABLE_NAME, fromId, toId);
    }

    public void addFollowedBy(String fromId, String toId) throws IOException {
        addRelation(FOLLOWED_TABLE_NAME, fromId, toId);
    }

    public void addRelation(TableName tableName, String fromId, String toId) throws IOException {

        Table t = connection.getTable(tableName);

        Put p = new Put(mkRowKey(fromId, toId));
        p.addColumn(RELATION_FAM, FROM, Bytes.toBytes(fromId));
        p.addColumn(RELATION_FAM, TO, Bytes.toBytes(toId));
        t.put(p);

        t.close();
    }

    public List<HBaseIA.TwitBase.model.Relation> listFollows(String fromId) throws IOException {
        return listRelations(FOLLOWS_TABLE_NAME, fromId);
    }

    public List<HBaseIA.TwitBase.model.Relation> listFollowedBy(String fromId) throws IOException {
        return listRelations(FOLLOWED_TABLE_NAME, fromId);
    }

    public List<HBaseIA.TwitBase.model.Relation> listRelations(TableName tableName, String fromId) throws IOException {

        Table t = connection.getTable(tableName);
        String rel = tableName.equals(FOLLOWS_TABLE_NAME) ? "->" : "<-";

        byte[] startKey = mkRowKey(fromId);
        byte[] endKey = Arrays.copyOf(startKey, startKey.length);
        endKey[Md5Utils.MD5_LENGTH - 1]++;
        Scan scan = new Scan(startKey, endKey);
        scan.addColumn(RELATION_FAM, TO);
        scan.setMaxVersions(1);

        ResultScanner results = t.getScanner(scan);
        List<HBaseIA.TwitBase.model.Relation> ret
                = new ArrayList<HBaseIA.TwitBase.model.Relation>();
        for (Result r : results) {
            Cell cell = r.getColumnLatestCell(RELATION_FAM, TO);
            String toId = Bytes.toString(CellUtil.cloneValue(cell));
            ret.add(new Relation(rel, fromId, toId));
        }

        t.close();
        return ret;
    }

    @SuppressWarnings("unused")
    public long followedByCountScan(String user) throws IOException {
        Table followed = connection.getTable(FOLLOWED_TABLE_NAME);

        final byte[] startKey = Md5Utils.md5sum(user);
        final byte[] endKey = Arrays.copyOf(startKey, startKey.length);
        endKey[endKey.length - 1]++;
        Scan scan = new Scan(startKey, endKey);
        scan.setMaxVersions(1);

        long sum = 0;
        ResultScanner rs = followed.getScanner(scan);
        for (Result r : rs) {
            sum++;
        }
        return sum;
    }

    private static class Relation extends HBaseIA.TwitBase.model.Relation {

        private Relation(String relation, String from, String to) {
            this.relation = relation;
            this.from = from;
            this.to = to;
        }
    }
}
