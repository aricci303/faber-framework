package scenarios;

import faber.agent.LlmProfiles;

public class ProfileParser {

	public static faber.agent.LlmProfile fetchProfile(String[] args) {
		faber.agent.LlmProfile profile = null;
		if (args.length > 0) {
        	if (args[0].startsWith("--llm-profile=")) { 
        		var profileName = args[0].substring(14).trim(); 
        		if (profileName.isEmpty()) {
        			throw new IllegalArgumentException("--llm-profile takes the name of a profile of the catalog (faber/agent/llm-profiles.json)"); 
        		}
                profile = faber.agent.LlmProfiles.resolve(profileName, null, null);  // an unknown profile or overlay fails here, as a launch error
        	}
        } else {
        	profile = LlmProfiles.defaultProfile();
        }	
		return profile;
	}

}
