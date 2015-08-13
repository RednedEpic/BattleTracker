package mc.alk.tracker.executors;

import java.util.List;

import mc.alk.tracker.TrackerInterface;
import mc.alk.tracker.controllers.MessageController;
import mc.alk.tracker.objects.Stat;
import mc.alk.tracker.objects.StatType;
import mc.alk.tracker.objects.VersusRecords.VersusRecord;
import mc.alk.tracker.objects.WLT;
import mc.alk.tracker.objects.WLTRecord;
import mc.alk.tracker.util.TimeUtil;
import mc.alk.v1r7.executors.CustomCommandExecutor;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;


public class TrackerExecutor extends CustomCommandExecutor {
	final TrackerInterface ti;
	public static final int MAX_RECORDS = 100;
	public TrackerExecutor(TrackerInterface ti) {
		super();
		this.ti =ti;
	}

	@MCCommand(cmds={"top"}, usage="top [x] [team size]")
	public boolean showTopXOther(CommandSender sender, String[] args){
		int x = 5;
		StatType st = null;
		if (args.length > 1){
			int xIndex = 1;
			st = StatType.fromName(args[1]);
			if (st != null){
				xIndex = 2;
			}
			if (args.length > xIndex)
				try {x = Integer.valueOf(args[xIndex]);}catch (Exception e){}
		}

		if (x<=0 || x > 100){
			return sendMessage(sender,MessageController.getMsg("xBetween", MAX_RECORDS));}
		List<Stat> stats = st == null ? ti.getTopXRating(x) : ti.getTopX(st, x);
		String stname = st == null ? "Rating" : st.getName();
		int min = Math.min(x, stats.size());
		if (min==0){
			return sendMessage(sender,MessageController.getMsg("noRecordsInTable", ti.getInterfaceName()));}
		sendMessage(sender,"&4=============== &6"+ti.getInterfaceName()+" "+stname+"&4===============");

		Stat stat;
		for (int i=0;i<min;i++){
			stat = stats.get(i);
			sendMessage(sender,"&6"+(i+1)+"&e: &c" + stat.getName()+"&6["+stat.getRating()+"] &eWins(&6"+stat.getWins()+
					"&e),Losses(&8"+stat.getLosses()+"&e),Streak(&b"+stat.getStreak()+"&e) W/L(&c"+String.format("%.2f", stat.getKDRatio())+"&e)");
		}
		return true;
	}

	@MCCommand(cmds={"versus","vs"}, usage="vs <player>")
	public boolean versus(Player player1, String player2){
		return versus(player1, player1.getName(), player2, 5);
	}

	@MCCommand(cmds={"versus","vs"}, usage="vs <player> <# records>")
	public boolean versus(Player player1, String player2, Integer nRecords){
		return versus(player1, player1.getName(), player2, nRecords);
	}

	private boolean versus(CommandSender sender, String player1, String player2, Integer x) {
		if (x<=0 || x > 100){
			return sendMessage(sender,MessageController.getMsg("xBetween", MAX_RECORDS));}

		Stat stat1 = findStat(player1);
		if (stat1 == null){
			return sendMessage(sender,MessageController.getMsg("recordNotFound", player1));}
		Stat stat2 = findStat(player2);
		if (stat2 == null){
			return sendMessage(sender,MessageController.getMsg("recordNotFound", player2));}

		ti.save(stat1,stat2);

		VersusRecord or = stat1.getRecordVersus(stat2);
		sendMessage(sender,"&4======================== &6"+ti.getInterfaceName()+" &4========================");
		sendMessage(sender,"&4================ &6"+stat1.getName()+" ("+stat1.getRating()+")&e vs &6" +
				stat2.getName()+"("+stat2.getRating()+") &4================");
		sendMessage(sender,"&eOverall Record (&2"+or.wins +" &e:&8 "+or.losses+" &e)");
		List<WLTRecord> records = ti.getVersusRecords(stat1.getName(), stat2.getName(),x);
		int min = Math.min(x, records.size());
		for (int i=0;i< min;i++){
			WLTRecord wlt = records.get(i);
			final String color = wlt.wlt == WLT.WIN ? "&2" : "&8";
			sendMessage(sender,color+wlt.wlt +"&e : &6" + TimeUtil.convertLongToDate(wlt.date));
		}
		return true;
	}

	@MCCommand(cmds={"addKill"},op=true,usage="addkill <player1> <player2>: this is a debugging method")
	public boolean addKill(CommandSender sender, String p1, String p2){
		Stat stat = ti.loadPlayerRecord(p1);
		Stat stat2 = ti.loadPlayerRecord(p2);
		if (stat == null || stat2 == null){
			sender.sendMessage("Player not found");
			return true;}

		ti.addStatRecord(stat, stat2, WLT.WIN);
		try {
			VersusRecord or = stat.getRecordVersus(stat2);
			sendMessage(sender, stat.getName()+ " versus " + stat2.getName()+" (&4"+or.wins +"&e:&8"+or.losses+"&e)");
		} catch(Exception e){

		}

		return true;
	}

	protected boolean addKill(CommandSender sender, Player p1, Player p2) {
		ti.addPlayerRecord(p1.getName(), p2.getName(), WLT.WIN);
		return sendMessage(sender, "Added kill " + p1.getDisplayName() + " wins over " + p2.getDisplayName());
	}

	protected String getFullStatMsg(Stat stat) {
		StringBuilder sb = new StringBuilder();
		sb.append("&5"+stat.getName() +"&6["+stat.getRating()+"] &eWins(&6"+stat.getWins()+"&e),Losses(&8"+stat.getLosses()+
				"&e),Streak(&b"+stat.getStreak()+"&e),MaxStreak(&7"+stat.getMaxStreak()+"&e) W/L(&c"+String.format("%.2f", stat.getKDRatio())+"&e)");
		return sb.toString();
	}

	protected String getStatMsg(Stat stat1, Stat stat2) {
		StringBuilder sb = new StringBuilder();
		sb.append(getFullStatMsg(stat2));
		VersusRecord or = stat1.getRecordVersus(stat2);
		sb.append("record versus (&4"+or.wins +"&e:&8"+or.losses+"&e)");
		return sb.toString();
	}

	@MCCommand()
	public boolean showStatsSelf(Player p) {
		Stat stat = ti.loadRecord(p);
		String msg = getFullStatMsg(stat);
		return sendMessage(p, msg);
	}

	@MCCommand
	public boolean showStatsOther(CommandSender sender, OfflinePlayer p) {
		/// Try to find the stat from what they typed
		Stat stat = findStat(p.getName());
		if (stat == null){ /// Find a player matching that name, hopefully
			return sendMessage(sender,MessageController.getMsg("recordNotFound", p.getName()));}
		String msg=null;
		if (sender instanceof Player){
			Stat selfStat = ti.loadRecord((Player)sender);
			if (selfStat == null){ ///
				sendMessage(sender, "&cYou have no records, Showing record for &6"+stat.getName());
				msg = getFullStatMsg(stat);
			} else {
				msg = getStatMsg(selfStat,stat);
			}
		} else {
			sendMessage(sender, "&2Showing record for &6" + stat.getName());
			msg = getFullStatMsg(stat);
		}
		return sendMessage(sender, msg);
	}

	public Stat findStat(String name){
		Stat stat = ti.getRecord(name);
		if (stat == null){ /// Find a player matching that name, hopefully
			OfflinePlayer op = findOfflinePlayer(name);
			if (op == null){
				return null;}
			stat = ti.loadRecord(op);
		}
		return stat;
	}

}
