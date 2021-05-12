package com.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("example")
public interface IdleTextNotifierConfig extends Config
{
	@ConfigItem(
		keyName = "accountsid",
		name = "Account SID",
		description = "Twilio Account SID"
	)
	default String accountSID()
	{
		return "";
	}

	@ConfigItem(
			keyName = "authtoken",
			name = "Auth Token",
			description = "Twilio Authorization Token"
	)
	default String authToken()
	{
		return "";
	}

	@ConfigItem(
			keyName = "twilionumber",
			name = "Twilio Number",
			description = "Twilio Number"
	)
	default String twilioNumber()
	{
		return "";
	}

	@ConfigItem(
			keyName = "targetnumber",
			name = "Target Number",
			description = "Phone number receiving the texts"
	)
	default String targetNumber() { return ""; }
}
