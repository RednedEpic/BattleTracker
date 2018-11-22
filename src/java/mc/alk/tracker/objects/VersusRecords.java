package mc.alk.tracker.objects;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import mc.alk.tracker.objects.VersusRecords.VersusRecord;
import mc.alk.tracker.serializers.SQLInstance;
import mc.alk.tracker.util.Cache;
import mc.alk.tracker.util.Cache.CacheObject;
import mc.alk.tracker.util.Cache.CacheSerializer;

import org.apache.commons.lang3.mutable.MutableBoolean;

public class VersusRecords implements CacheSerializer<List<String>,VersusRecord>{
	String id;
	SQLInstance sql;

	Cache<List<String>, VersusRecord> totals = new Cache<List<String>, VersusRecord>(this);
	HashMap<String,List<WLTRecord>> ind_records;
	boolean saveIndividualRecord = true;

	public VersusRecords(String myid, SQLInstance sql){
		this.id = myid;
		this.sql = sql;
	}

	public static class VersusRecord extends CacheObject<List<String>,VersusRecord>{
		public int wins,losses, ties;
		final public List<String> ids = new ArrayList<String>(2);
		public VersusRecord(String id1, String id2){
			ids.add(id1); ids.add(id2);
		}


		public List<String> getKey() {return ids;}
		static public List<String>getKey(String id, String oid){return Arrays.asList(new String[]{id,oid});}
		public void incWin() {wins++;setDirty();}
		public void incLosses() {losses++;setDirty();}
		public void incTies() {ties++;setDirty();}
	}

	private List<WLTRecord> getIndRecord(String opponent){
		if (ind_records == null)
			ind_records = new HashMap<String,List<WLTRecord>>();
		List<WLTRecord> record = ind_records.get(opponent);
		if (record == null) {
			record = new ArrayList<WLTRecord>();
			ind_records.put(opponent, record);
		}
		return record;
	}

	public void addWin(String oid) {
		totals.get(VersusRecord.getKey(id, oid)).incWin();
		if (saveIndividualRecord)
			getIndRecord(oid).add(new WLTRecord(WLT.WIN));
	}


	public void addLoss(String oid) {
		totals.get(VersusRecord.getKey(id, oid)).incLosses();
		if (saveIndividualRecord)
			getIndRecord(oid).add(new WLTRecord(WLT.LOSS));
	}

	public void addTie(String oid) {
		totals.get(VersusRecord.getKey(id, oid)).incTies();
		if (saveIndividualRecord)
			getIndRecord(oid).add(new WLTRecord(WLT.TIE));
	}

	public VersusRecord getRecordVersus(String opponentId) {
		return totals.get(new ArrayList<String>(VersusRecord.getKey(id, opponentId)));
	}

	public VersusRecord load(List<String> key, MutableBoolean dirty, Object... varArgs) {
		VersusRecord or = sql.getVersusRecord(key.get(0), key.get(1));
		if (or != null){
			or.setCache(totals);
			dirty.setValue(false);
		} else {
			or = new VersusRecord(key.get(0), key.get(1));
			dirty.setValue(true);
		}
		return or;
	}

	public void save(List<VersusRecord> types) {
		sql.realsaveVersusRecords(types);
		sql.saveIndividualRecords(id, ind_records);
		ind_records = null;
	}

	public Collection<VersusRecord> getOverallRecords() {
		if (totals == null)
			return null;
		return totals.values();
	}

	public HashMap<String,List<WLTRecord>> getIndividualRecords() {
		return ind_records;
	}

	public void setIndividualRecords(HashMap<String,List<WLTRecord>> ind) {
		this.ind_records = ind;
	}

	public void flushOverallRecords() {
		totals.flush();
	}

	public void setSaveIndividual(boolean saveIndividualRecord) {
		this.saveIndividualRecord = saveIndividualRecord;
	}

}
