package mc.alk.tracker.objects;

import mc.alk.tracker.Defaults;
import mc.alk.tracker.controllers.TrackerImpl;
import mc.alk.tracker.events.MaxRatingChangeEvent;
import mc.alk.tracker.events.WinStatChangeEvent;
import mc.alk.tracker.objects.VersusRecords.VersusRecord;
import mc.alk.tracker.ranking.EloCalculator;
import mc.alk.tracker.util.Cache.CacheObject;
import mc.alk.tracker.util.Util;
import mc.alk.v1r7.util.Log;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;


public abstract class Stat extends CacheObject<String,Stat>{
	protected String strid = null;
	protected String name;
	protected float rating = EloCalculator.DEFAULT_ELO;
	protected float maxRating = rating;
	protected int wins = 0, losses= 0, ties = 0;
	protected int streak = 0, maxStreak =0;
	protected int count = 1; /// How many members are in the team
	boolean hide = false;

	List<String> members ;

	VersusRecords vRecord = null;
	private TrackerImpl parent;

	public String getKey() {
		if (strid.length() > 32 )
			Util.printStackTrace();

		return getStrID();
	}

	public List<String> getMembers() {
		return members;
	}

	public String getStrID(){return strid;}
	public void setName(String name) {
		this.name = name; setDirty();
		if (strid != null && strid.length() > 32 ){
			Log.err("NAME = " + name +"    strid=" + strid);
			Util.printStackTrace();
		}

	}

	public String getName(){return name;}

	public void setWins(int wins) {this.wins = wins;setDirty();}
	public int getWins() {return wins;}
	public void setStreak(int i){streak = i;setDirty();}
	public int getStreak() { return streak;}
	public void setLosses(int i){losses = i;setDirty();}
	public int getLosses() {return losses;}
	public void setTies(int i){ties = i;setDirty();}
	public int getTies() {return ties;}
	public int getCount() { return count;}
	public void setCount(int i){count = i;setDirty();}
	public float getKDRatio() { return ((float) wins) / losses;}
	public void incLosses() {
		streak = 0;
		losses++;
		setDirty();
	}
	public void incTies(){
		streak = 0;
		ties++;
		setDirty();
	}
	public void incWins() {
		wins++;
		incStreak();
		setDirty();
	}
	public void incStreak() {
		streak++;
		if (streak > maxStreak)
			maxStreak = streak;
		setDirty();
	}
	public void endStreak() {streak=0;setDirty();}
	public int getMaxStreak() {return maxStreak;}
	public void setMaxStreak(int maxStreak) {this.maxStreak = maxStreak;setDirty();}
	public void setMaxRating(int maxRating) {this.maxRating = maxRating;setDirty();}

	public int getRating() {
        return (int) rating;
    }
	public int getMaxRating() {return (int) maxRating;}

	public void setRating(float rating){
		this.rating = rating;
		if (this.rating > maxRating){
			int threshold =  ( ((int)maxRating) /100) *100 + 100;
			double oldRating = maxRating;
			maxRating = this.rating;
			if (maxRating < threshold && this.rating >= threshold){
				maxRating = this.rating;
				new MaxRatingChangeEvent(parent,this,oldRating).callSyncEvent();
			}
		}
		setDirty();
	}

	@Override
	public boolean equals( Object obj ) {
		if(this == obj) return true;
		if((obj == null) || (obj.getClass() != this.getClass())) return false;
		TeamStat test = (TeamStat)obj;
		return this.compareTo(test) == 0;
	}

	/**
	 * Teams are ordered list of strings
	 */
	public int compareTo(TeamStat o) {
		return this.strid.compareTo(o.strid);
	}

	public VersusRecords getRecord(){
		if (vRecord == null)
			vRecord = new VersusRecords(getKey(),parent.getSQL()) ;
		return vRecord;
	}

	public void win(Stat ts) {
		if (Defaults.DEBUG_ADD_RECORDS) System.out.println("BT Debug: win: tsID="+ts.getStrID() +
				"  parent=" + parent +"  " + (parent !=null? parent.getSQL() : "null") + " key=" + getKey());
		wins++;
		streak++;
		if (streak > maxStreak){
			maxStreak=streak;}
		getRecord().addWin(ts.getStrID());
		new WinStatChangeEvent(parent,this,ts).callSyncEvent();
		setDirty();
	}

	public void loss(Stat ts) {
		losses++;
		streak =0;
		getRecord().addLoss(ts.getStrID());
		setDirty();
	}

	public void tie(Stat ts) {
		ties++;
		getRecord().addTie(ts.getStrID());
		setDirty();
	}

	public static String getKey(Player players){
		return players.getName();
	}
	public static String getKey(String player){
		return player;
	}

	protected static String getKey(List<String> playernames){
		Collections.sort(playernames);
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (String s : playernames){
			if (!first) sb.append(",");
			sb.append(s);
			first = false;
		}
		if (sb.length() > 32){
			return sb.toString().hashCode() + "";
		}

		return sb.toString();
	}

	public VersusRecord getRecordVersus(Stat stat) {
		/// We cant get a record if we have no way of loading
		if (vRecord == null){
			vRecord = getRecord();}
		return vRecord.getRecordVersus(stat.getStrID());
	}

	protected void createName(){
		if (name != null && !StringUtils.isEmpty(name)) /// We have a specified name, dont use the naive append all players together
			return;
		//		System.out.println("name="+name);
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (String n : members){
			if (!first) sb.append(",");
			sb.append(n);
			first = false;
		}
		name = sb.toString();
		//		System.out.println("afterwards name="+name);
	}

	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("[Team=" + getName() + " ["+getRating()+":"+getKDRatio()+"](" + getWins() + ":" + getLosses() + ":" + getStreak() +") id="+strid +
				",count="+count+",p.size="+ (members==null?"null" : members.size()) );
		if (vRecord != null){
			sb.append("  [Kills]= ");
			HashMap<String,List<WLTRecord>> records = vRecord.getIndividualRecords();
			if (records != null){
				for (String tk : records.keySet()){
					sb.append(tk +":" + vRecord.getIndividualRecords().get(tk) +" ," );}
			}
		}
		sb.append("]");
		return sb.toString();
	}

	public VersusRecords getRecordSet() {
		return vRecord;
	}

	public void setParent(TrackerImpl parent) {
		this.parent = parent;
	}

	public void setSaveIndividual(boolean saveIndividualRecord) {
		if (vRecord != null)
			vRecord.setSaveIndividual(saveIndividualRecord);
	}

	public int getFlags() {
		return hide ? 1 : 0;
	}

	public void setFlags(int flags) {
		hide = (flags == 0 ? false : true);
	}

	public boolean isHidden(){
		return hide;
	}

	public void hide(boolean hide) {
		if (this.hide != hide){
			setDirty();
			this.hide = hide;
		}
	}

	public float getStat(StatType statType) {
		switch(statType){
		case WINS: case KILLS: return getWins();
		case LOSSES: case DEATHS: return getLosses();
		case RANKING: case RATING: return getRating();
		case KDRATIO : case WLRATIO : return getKDRatio();
		case MAXRANKING : case MAXRATING : return getMaxRating();
		case MAXSTREAK: return getMaxStreak();
		case STREAK: return getStreak();
		case TIES: return getTies();
		default:
			break;
		}
		return 0;
	}
}
