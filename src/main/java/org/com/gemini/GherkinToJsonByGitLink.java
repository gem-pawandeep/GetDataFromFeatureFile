package org.com.gemini;

import gherkin.util.FixJava;
import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.Exception;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.cucumber.messages.types.SourceMediaType.TEXT_X_CUCUMBER_GHERKIN_PLAIN;

public class GherkinToJsonByGitLink {
    static JSONArray ScenarioData = new JSONArray();

    public static List<String> getApiUrl(String gitUrl, String branch, String BearerToken) {
        String apiUrl = "";
        List<String> FeatureUrls = new ArrayList<>();
        try {
            Pattern p = Pattern.compile("https://(.+)/(.+)/(.+)");
            Matcher m = p.matcher(gitUrl);
            if (m.find()) {
                String GitServer = m.group(1);
                String username = m.group(2);
                String repoName = m.group(3);
                apiUrl = "https://api." + GitServer + "/repos/" + username + "/" + repoName + "/git/trees/" + branch + "?recursive=1";
                URL url = new URL(apiUrl);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                if (BearerToken != null) {
                    con.setRequestProperty("Authorization", "Bearer " + BearerToken);
                }
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                JSONObject response = new JSONObject(content.toString());
                JSONArray tree = (JSONArray) response.get("tree");
                String contentUrl = "https://api." + GitServer + "/repos/" + username + "/" + repoName + "/contents/";
                for (int i = 0; i < tree.length(); i++) {
                    JSONObject temp = (JSONObject) tree.get(i);
                    if ((temp.get("path").toString()).contains(".feature")) {
                        FeatureUrls.add(contentUrl + temp.get("path").toString() + "?ref=" + branch);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return FeatureUrls;
    }

    public static List<String> getApiUrl(String gitUrl, String branch) {
        return getApiUrl(gitUrl, branch, null);
    }

    public static List<String> GetAllFeatureFileUrlsFromGit(List<String> ApiUrl, String BearerToken) {
        List<String> featureFiles = new ArrayList<>();
        try {
            for (String urls : ApiUrl) {
                URL url = new URL(urls);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                if (BearerToken != null) {
                    con.setRequestProperty("Authorization", "Bearer " + BearerToken);
                }
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                JSONObject response = new JSONObject(content.toString());
                featureFiles.add(response.getString("download_url"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return featureFiles;
    }

    public static List<String> GetAllFeatureFileUrlsFromGit(List<String> ApiUrl) {
        return GetAllFeatureFileUrlsFromGit(ApiUrl, null);
    }

    public static Feature GetFeatureFile(String GitDUrl, String BearerToken) {
        try {
            URL Url = new URL(GitDUrl);
            HttpURLConnection Http = (HttpURLConnection) Url.openConnection();
            if (BearerToken != null) {
                Http.setRequestProperty("Authorization", "Bearer " + BearerToken);
            }
            Map<String, List<String>> Header = Http.getHeaderFields();
            for (String header : Header.get(null)) {
                if (header.contains(" 302 ") || header.contains(" 301 ")) {
                    GitDUrl = Header.get("Location").get(0);
                    Url = new URL(GitDUrl);
                    Http = (HttpURLConnection) Url.openConnection();
                    Header = Http.getHeaderFields();
                }
            }
            InputStream Stream = Http.getInputStream();
            String gherkin = FixJava.readReader(new InputStreamReader(Stream, StandardCharsets.UTF_8));
            Envelope envelope = Envelope.of(new Source("Features.feature", gherkin, TEXT_X_CUCUMBER_GHERKIN_PLAIN));
            GherkinDocument gherkinDocument = GherkinParser.builder().includeSource(false).includePickles(false).build().parse(envelope).findFirst().flatMap(Envelope::getGherkinDocument).get();
            return gherkinDocument.getFeature().get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Feature GetFeatureFile(String GitDUrl) {
        return GetFeatureFile(GitDUrl, null);
    }

    public static void ConvertFeatureJson(Feature features) {
        try {
            JSONObject currScenarioData = new JSONObject();
            JSONArray Steps = new JSONArray();
            JSONArray tags = new JSONArray();
            JSONObject Examples = new JSONObject();
            JSONArray ExamplesHeaders = new JSONArray();
            JSONArray ExamplesData = new JSONArray();
            for (FeatureChild iterator : features.getChildren()) {
                // Tags Name
                for (Tag featureChild : iterator.getScenario().get().getTags()) {
                    tags.put(featureChild.getName());
                }
                if (!tags.isEmpty()) {
                    currScenarioData.put("Tags", tags);
                }
                //Steps
                for (Step step : iterator.getScenario().get().getSteps()) {
                    Steps.put(step.getKeyword() + " " + step.getText());
                }
                if (!Steps.isEmpty()) {
                    currScenarioData.put("Steps", Steps);
                }
                //Examples
                for (Examples examples : iterator.getScenario().get().getExamples()) {
                    //Examples Headers
                    for (TableRow table : examples.getTableHeader().stream().toList()) {
                        for (TableCell ts : table.getCells()) {
                            ExamplesHeaders.put(ts.getValue());
                        }
                    }
                    Examples.put("Headers", ExamplesHeaders);
                    //Example Data
                    for (TableRow tableRow : examples.getTableBody()) {
                        JSONArray temp = new JSONArray();
                        for (TableCell s : tableRow.getCells()) {
                            temp.put(s.getValue());
                        }
                        ExamplesData.put(temp);
                    }
                    Examples.put("data", ExamplesData);
                }
                if (!Examples.isEmpty()) {
                    currScenarioData.put("Examples", Examples);
                }
                if (!ExamplesData.isEmpty()) {
                    currScenarioData.put("TestCase", iterator.getScenario().get().getName());
                    currScenarioData.put("type", "Scenario Outline");
                } else {
                    currScenarioData.put("TestCase", iterator.getScenario().get().getName());
                    currScenarioData.put("type", "Scenario");
                }
                currScenarioData.put("feature", features.getName());
                // Scenario Data
                ScenarioData.put(currScenarioData);
                //Re-Initialize Scenarios Variables
                currScenarioData = new JSONObject();
                Steps = new JSONArray();
                tags = new JSONArray();
                Examples = new JSONObject();
                ExamplesHeaders = new JSONArray();
                ExamplesData = new JSONArray();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static JSONArray GetFeatureJsonFromGit(String GitProjectUrl, String branchName, String BearerToken) {
        try {
            if (BearerToken != null) {
                List<String> s = getApiUrl(GitProjectUrl, branchName, BearerToken);
                List<String> featureUrl = GetAllFeatureFileUrlsFromGit(s, BearerToken);
                for (String url : featureUrl) {
                    Feature f = GetFeatureFile(url, BearerToken);
                    ConvertFeatureJson(f);
                }
            } else {
                List<String> s = getApiUrl(GitProjectUrl, branchName);
                List<String> featureUrl = GetAllFeatureFileUrlsFromGit(s);
                for (String url : featureUrl) {
                    Feature f = GetFeatureFile(url);
                    ConvertFeatureJson(f);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ScenarioData;
    }

    public static JSONArray GetFeatureJsonFromGit(String GitProjectUrl, String branchName) {
        return GetFeatureJsonFromGit(GitProjectUrl, branchName, null);
    }

    public static void main(String[] args){

        //Without authorization(Public repository)  GetFeatureJsonFromGit(gitProjectUrl,BranchName);

        System.out.println(GetFeatureJsonFromGit("https://github.com/gem-maulickbharadwaj/JewelAutomationBdd-master", "master"));

//        System.out.println(GetFeatureJsonFromGit("https://github.com/gem-pawandeep/GemEcoSystem-API-JV", "test"));

        //With Authorization(Private repository)  GetFeatureJsonFromGit(gitProjectUrl,BranchName,BearerToken);

//        System.out.println(GetFeatureJsonFromGit("https://github.com/gem-pawandeep/GemEcoSystem-API-JV", "master","enter your token"));

//        System.out.println(GetFeatureJsonFromGit("https://github.com/gem-pawandeep/GemEcoSystem-API-JV", "test","enter your token"));

    }

}