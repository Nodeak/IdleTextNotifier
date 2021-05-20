package com.discordNotifier;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.events.NotificationFired;
import okhttp3.*;
import net.runelite.api.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.io.IOException;

import static net.runelite.http.api.RuneLiteAPI.GSON;

@Slf4j
@PluginDescriptor(
	name = "Discord Notifier"
)
public class DiscordNotifier extends Plugin
{

	@Inject
	private Client client;

	@Inject
	private DiscordNotifierConfig config;

	@Inject
	private OkHttpClient okHttpClient;

	/**
	 * Send a message to Discord using the configured webhook
	 *
	 * Discord code is pulled and modified from:
	 * https://github.com/ATremonte/Discord-Level-Notifications/blob/8a49abe6fcc59fdebf66870e4cf3078234c13035/src/main/java/com/discordlevelnotifications/LevelNotificationsPlugin.java
	 *
	 * @param message The message to be sent
	 */
	private void sendDiscordMessage(String message) {
		if(config.webhook().trim().isEmpty()){
			log.warn("Missing Discord webhook. Cannot send message.");
			return;
		}

		DiscordWebhookBody discordWebhookBody = new DiscordWebhookBody();
		discordWebhookBody.setContent(message);

		HttpUrl url = HttpUrl.parse(config.webhook().trim());
		MultipartBody.Builder requestBodyBuilder = new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("payload_json", GSON.toJson(discordWebhookBody));

		RequestBody requestBody = requestBodyBuilder.build();
		Request request = new Request.Builder()
				.url(url)
				.post(requestBody)
				.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.error("Error submitting message to Discord webhook.", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				log.info("Successfully sent message to Discord.");
				response.close();
			}
		});
	}

	/**
	 * Subscribes to the NotificationFired event so that any RuneLite notification is also sent to Discord.
	 *
	 * https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/events/NotificationFired.java
	 *
	 * @param notificationFired The notification that was fired.
	 */
	@Subscribe
	public void onNotificationFired(NotificationFired notificationFired) {
		sendDiscordMessage(notificationFired.getMessage());
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("Starting Discord Notifier Plugin.");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Discord Notifier Plugin stopped!");
	}

	@Provides
	DiscordNotifierConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DiscordNotifierConfig.class);
	}
}
