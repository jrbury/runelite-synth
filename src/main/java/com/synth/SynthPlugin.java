package com.synth;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;
import net.runelite.http.api.RuneLiteAPI;
import net.runelite.http.api.loottracker.LootRecordType;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import net.runelite.api.ItemID;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static net.runelite.http.api.hiscore.HiscoreSkill.*;
import static net.runelite.http.api.RuneLiteAPI.GSON;
import static net.runelite.http.api.RuneLiteAPI.JSON;
import net.runelite.api.InventoryID;

@Slf4j
@PluginDescriptor(
	name = "Synth Twitch Tracking"
)
public class SynthPlugin extends Plugin
{
	private static final Pattern LEVEL_UP_PATTERN = Pattern.compile(".*Your ([a-zA-Z]+) (?:level is|are)? now (\\d+)\\.");
	private static final Pattern KILLCOUNT_PATTERN = Pattern.compile("Your (.+) (?:kill|harvest|lap|completion) count is: <col=ff0000>(\\d+)</col>");
	private static final Pattern RAIDS_PATTERN = Pattern.compile("Your completed (.+) count is: <col=ff0000>(\\d+)</col>");
	private static final Pattern WINTERTODT_PATTERN = Pattern.compile("Your subdued Wintertodt count is: <col=ff0000>(\\d+)</col>");
	private static final Pattern BARROWS_PATTERN = Pattern.compile("Your Barrows chest count is: <col=ff0000>(\\d+)</col>");
	private static final Pattern VALUABLE_DROP_PATTERN = Pattern.compile(".*Valuable drop: ([^<>]+)(?:</col>)?");
	private static final Pattern UNTRADEABLE_DROP_PATTERN = Pattern.compile(".*Untradeable drop: ([^<>]+)(?:</col>)?");
	private static final ImmutableList<String> PET_MESSAGES = ImmutableList.of("You have a funny feeling like you're being followed",
			"You feel something weird sneaking into your backpack",
			"You have a funny feeling like you would have been followed");
	private static final String UNKNONW_USER = "unknown";

	public static final int COLLECTION_LOG_GROUP_ID = 621;
	public static final int COLLECTION_LIST = 12;
	public static final int COLLECTION_VIEW = 35;

	private String lastBossKill;
	private HashMap<String, Integer> BossCount = new HashMap<String, Integer>();

	@Inject
	private Client client;

	@Inject
	private SynthConfig config;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Synth started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Synth stopped!");
	}

	@Subscribe
	public void onLootReceived(LootReceived loot) {
		log.info(loot.toString());

		if (loot.getType() != LootRecordType.NPC && loot.getType() != LootRecordType.EVENT && loot.getType() != LootRecordType.UNKNOWN) {
			return;
		}

		String username = getUsername();
		if (username == UNKNONW_USER) {
			return;
		}

		if (loot.getName() == "Barrows") {
			loot.setName("Barrows Chests");
		}

		LootDrop drop = new LootDrop(username, loot.getName(), loot.getCombatLevel(), getBossKC(loot.getName()), loot.getType(), loot.getItems());

		Request request = new Request.Builder()
				.url("http://optimus.jerod.tv:8080/api/v1/runelite/loot")
				.addHeader("Origin", "Runelite")
				.post(RequestBody.create(JSON, GSON.toJson(drop)))
				.build();
		try (Response response = RuneLiteAPI.CLIENT.newCall(request).execute()) {
			log.info(response.body().string());
		} catch(Exception e) {
			log.error(e.toString());
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM && event.getType() != ChatMessageType.TRADE)
		{
			return;
		}

		String message = event.getMessage();
		String boss = "";
		Integer count = 0;
		Boolean found = false;

		Matcher matcher = KILLCOUNT_PATTERN.matcher(message);
		if (matcher.find())
		{
			boss = matcher.group(1);
			count = Integer.parseInt(matcher.group(2));
			found = true;
		}

		matcher = WINTERTODT_PATTERN.matcher(message);
		if (matcher.find())
		{
			boss = "Wintertodt";
			count = Integer.parseInt(matcher.group(1));
			found = true;
		}

		matcher = RAIDS_PATTERN.matcher(message);
		if (matcher.find())
		{
			boss = matcher.group(1);
			count = Integer.parseInt(matcher.group(2));
			found = true;
		}

		matcher = BARROWS_PATTERN.matcher(message);
		if (matcher.find())
		{
			boss = "Barrows Chests";
			count = Integer.parseInt(matcher.group(1));
			found = true;
		}

		if (PET_MESSAGES.stream().anyMatch(message::contains))
		{
			String name = getUsername();
			if (name != UNKNONW_USER) {
				PetDrop drop = new PetDrop(name, lastBossKill, getBossKC(lastBossKill));

				Request request = new Request.Builder()
						.url("http://optimus.jerod.tv:8080/api/v1/runelite/pet")
						.addHeader("Origin", "Runelite")
						.post(RequestBody.create(JSON, GSON.toJson(drop)))
						.build();
				try (Response response = RuneLiteAPI.CLIENT.newCall(request).execute()) {
					log.info(response.body().string());
				} catch (Exception e) {
					log.error(e.toString());
				}
			}
		}

		if (found) {
			log.info(event.toString());

			BossCount.put(boss, count);
			lastBossKill = boss;

			String name = getUsername();
			if (name != UNKNONW_USER) {
				BossKill kill = new BossKill(name, boss, count);

				Request request = new Request.Builder()
						.url("http://optimus.jerod.tv:8080/api/v1/runelite/boss")
						.addHeader("Origin", "Runelite")
						.post(RequestBody.create(JSON, GSON.toJson(kill)))
						.build();
				try (Response response = RuneLiteAPI.CLIENT.newCall(request).execute()) {
					log.info(response.body().string());
				} catch (Exception e) {
					log.error(e.toString());
				}
			}
		}
	}

	private String getUsername() {
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return UNKNONW_USER;
		}
		return local.getName();
	}

	private Integer getBossKC(String boss) {
		try {
			return BossCount.get(boss);
		} catch	(Exception e) {
			log.error(e.toString());
			return -1;
		}
	}

	@Provides
	SynthConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SynthConfig.class);
	}
}
