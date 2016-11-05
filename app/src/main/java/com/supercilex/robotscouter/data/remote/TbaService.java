package com.supercilex.robotscouter.data.remote;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.crash.FirebaseCrash;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.supercilex.robotscouter.BuildConfig;
import com.supercilex.robotscouter.data.model.Team;
import com.supercilex.robotscouter.util.Constants;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public abstract class TbaService {
    private Team mTeam;
    private Context mContext;
    private TbaApi mTbaApi;

    public TbaService(Team team, Context context) {
        mTeam = team;
        mContext = context;

        getTeamInfo();
    }

    private void getTeamInfo() {
        mTbaApi = TbaApi.retrofit.create(TbaApi.class);
        Call<JsonObject> infoCall = mTbaApi.getTeamInfo(mTeam.getNumber(),
                                                        "frc2521:Robot_Scouter:" + BuildConfig.VERSION_NAME);
        infoCall.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    JsonObject result = response.body();

                    JsonElement teamNickname = result.get(Constants.TEAM_NICKNAME);
                    if (teamNickname != null && !teamNickname.isJsonNull()) {
                        mTeam.setName(teamNickname.getAsString());
                    }

                    JsonElement teamWebsite = result.get(Constants.TEAM_WEBSITE);
                    if (teamWebsite != null && !teamWebsite.isJsonNull()) {
                        mTeam.setWebsite(teamWebsite.getAsString());
                    }
                } else if (response.code() == 404) {
                    onFinished(mTeam, true);
                    return;
                } else {
                    onFinished(mTeam, false);
                    return;
                }

                getTeamMedia();
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                FirebaseCrash.report(t);
            }
        });
    }

    private void getTeamMedia() {
        // TODO: 09/11/2016 Make syncronized so doesn't have to wait for team info
        mTbaApi = TbaApi.retrofit.create(TbaApi.class);
        Call<JsonArray> mediaCall = mTbaApi.getTeamMedia(mTeam.getNumber(),
                                                         "frc2521:Robot_Scouter:" + BuildConfig.VERSION_NAME);
        mediaCall.enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(Call<JsonArray> call, Response<JsonArray> response) {
                if (response.isSuccessful()) {
                    JsonArray result = response.body();

                    for (int i = 0; i < result.size(); i++) {
                        JsonObject mediaObject = result.get(i).getAsJsonObject();
                        String mediaType = mediaObject.get("type").getAsString();

                        if (mediaType != null) {
                            if (mediaType.equals("imgur")) {
                                String url = "https://i.imgur.com/" + mediaObject.get("foreign_key")
                                        .getAsString() + ".png";

                                getMedia(url, mTeam);
                                break;
                            } else if (mediaType.equals("cdphotothread")) {
                                String url = "https://www.chiefdelphi.com/media/img/" + mediaObject.get(
                                        "details")
                                        .getAsJsonObject()
                                        .get("image_partial")
                                        .getAsString();

                                getMedia(url, mTeam);
                                break;
                            }
                        }
                    }
                } else {
                    onFinished(mTeam, false);
                    return;
                }

                onFinished(mTeam, true);
            }

            @Override
            public void onFailure(Call<JsonArray> call, Throwable t) {
                FirebaseCrash.report(t);
            }
        });
    }

    private void getMedia(String url, Team team) {
        team.setMedia(url);
        Glide.with(mContext).load(url).diskCacheStrategy(DiskCacheStrategy.ALL);
    }

    public abstract void onFinished(Team team, boolean isSuccess);
}