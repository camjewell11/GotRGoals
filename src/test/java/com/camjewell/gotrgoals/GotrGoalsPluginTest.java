package com.camjewell.gotrgoals;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GotrGoalsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GotrGoalsPlugin.class);
		RuneLite.main(args);
	}
}
