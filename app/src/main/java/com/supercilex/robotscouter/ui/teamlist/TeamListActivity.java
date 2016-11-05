package com.supercilex.robotscouter.ui.teamlist;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.database.FirebaseIndexRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IProfile;
import com.supercilex.robotscouter.R;
import com.supercilex.robotscouter.data.model.Team;
import com.supercilex.robotscouter.util.Constants;
import com.supercilex.robotscouter.util.FirebaseUtils;
import com.supercilex.robotscouter.util.TagUtils;

import java.util.Arrays;

import static com.firebase.ui.auth.ui.AcquireEmailHelper.RC_SIGN_IN;

// TODO: 06/21/2016 Add sign out flow
// TODO: 08/10/2016 add Firebase analytics to menu item clicks so I know what stuff to put on top
// TODO: 08/10/2016 make users enter their team number to setup database with their team as example. Also add Firebase analytics to make sure this isn't getting rid of users.
// TODO: 08/31/2016 Look for FirebaseCrash.report() and set up FirebaseCrash.log(). log will put logs for the crash.

public class TeamListActivity extends AppCompatActivity {
    private static final String MANAGER_STATE = "manager_state";
    private static final String COUNT = "count";

    private FirebaseRecyclerAdapter mAdapter;
    private LinearLayoutManager mManager;

    private FirebaseAuth.AuthStateListener mAuthStateListener;

    private Toolbar mToolbar;
    private Drawer mDrawer;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_NoActionBar);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_team_list);
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new NewTeamDialogFragment().show(getSupportFragmentManager(),
                                                 TagUtils.getTag(this));
            }
        });


//        if (!isSignedIn()) {
//            signInAnonymously();
//        } else {
//            attachRecyclerViewAdapter();
//        }

        // Example Tasks api usage
//        Tasks.call(new Executor() {
//            @Override
//            public void execute(Runnable runnable) {
//                new Thread(runnable).start();
//            }
//        }, new Callable<Scout>() {
//            @Override
//            public Scout call() throws Exception {
//                double i = 0;
//                Log.e(TagUtils.getTag(TeamListActivity.this), "started");
//                while (true) {
//                    i++;
//                    if (i >= 1000000000L) {
//                        break;
//                    }
//                }
//                Log.e(TagUtils.getTag(TeamListActivity.this), "long code executed");
//                return null;
//            }
//        }).addOnSuccessListener(new OnSuccessListener<Scout>() {
//            @Override
//            public void onSuccess(Scout scout) {
//                Log.e(TagUtils.getTag(TeamListActivity.this), "onSuccess");
//            }
//        });

        final RecyclerView teams = (RecyclerView) findViewById(R.id.team_list);
        teams.setHasFixedSize(true);
        mManager = new LinearLayoutManager(this);
        teams.setLayoutManager(mManager);
        // TODO: 09/03/2016 how to know when user is at bottom of RecyclerView for pagination
        mAdapter = new FirebaseIndexRecyclerAdapter<Team, TeamHolder>(
                Team.class,
                R.layout.activity_team_list_row_layout,
                TeamHolder.class,
                FirebaseUtils.getDatabase()
                        .child(Constants.FIREBASE_TEAM_INDEXES)
                        .child(FirebaseUtils.getUid()),
                FirebaseUtils.getDatabase().child(Constants.FIREBASE_TEAMS)) {
            @Override
            public void populateViewHolder(TeamHolder teamHolder, Team team, int position) {
                String teamNumber = team.getNumber();
                String key = getRef(position).getKey();

                teamHolder.setTeamNumber(teamNumber);
                teamHolder.setTeamName(team.getName(),
                                       TeamListActivity.this.getString(R.string.unknown_team));
                teamHolder.setTeamLogo(TeamListActivity.this, team.getMedia());
                teamHolder.setListItemClickListener(TeamListActivity.this, teamNumber, key);
                teamHolder.setCreateNewScoutListener(TeamListActivity.this, teamNumber, key);

                team.fetchLatestData(TeamListActivity.this, key);
            }

            @Override
            protected void onCancelled(DatabaseError databaseError) {
                FirebaseCrash.report(databaseError.toException());
            }
        };

        teams.setAdapter(mAdapter);

        if (savedInstanceState != null) {
            mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    if (mAdapter.getItemCount() >= savedInstanceState.getInt(COUNT)) {
                        mManager.onRestoreInstanceState(savedInstanceState.getParcelable(
                                MANAGER_STATE));
                        mAdapter.unregisterAdapterDataObserver(this);
                    }
                }
            });
        }

        // TODO: 08/05/2016 remove and get signInAnonymous to work
        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                if (firebaseAuth.getCurrentUser() != null) {
                    teams.setAdapter(mAdapter);
                } else {
                    // TODO show a tutorial (pretend first time app start)
                    teams.setAdapter(null);
                }
                initializeDrawer();
            }
        };
        FirebaseUtils.getAuth().addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mAdapter != null) {
            outState.putParcelable(MANAGER_STATE, mManager.onSaveInstanceState());
            outState.putInt(COUNT, mAdapter.getItemCount());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAuthStateListener != null) {
            FirebaseUtils.getAuth().removeAuthStateListener(mAuthStateListener);
        }

        if (mAdapter != null) {
            mAdapter.cleanup();
        }
    }

    @Override
    public void onBackPressed() {
        if (mDrawer.isDrawerOpen()) {
            mDrawer.closeDrawer();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.team_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                // user is signed in!
                Snackbar.make(findViewById(android.R.id.content),
                              R.string.successfully_signed_in,
                              Snackbar.LENGTH_LONG).show();

                DatabaseReference ref = FirebaseUtils.getDatabase()
                        .child("users")
                        .child(FirebaseUtils.getUid());
                ref.child("name").setValue(FirebaseUtils.getUser().getDisplayName());
                ref.child("provider").setValue(FirebaseUtils.getUser().getProviderId());
                ref.child("email").setValue(FirebaseUtils.getUser().getEmail());
            }
        }
    }

    // TODO: 08/29/2016 move all old uid to new uid and delete old https://github.com/firebase/FirebaseUI-Android/issues/123
    private void signInAnonymously() {
        FirebaseUtils.getAuth().signInAnonymously()
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            initializeDrawer();
                        } else {
                            Snackbar.make(findViewById(android.R.id.content),
                                          "Sign In Failed",
                                          Snackbar.LENGTH_SHORT)
                                    .show();
                            FirebaseCrash.report(task.getException());
                        }
                    }
                });
    }

    // TODO: 08/04/2016 get rid of this
    private void initializeDrawer() {
        AccountHeader header;
        if (FirebaseUtils.getUser() != null)
            if (!FirebaseUtils.getUser().isAnonymous()) {
                // already signed in
                FirebaseUser user = FirebaseUtils.getUser();
                header = new AccountHeaderBuilder()
                        .withActivity(this)
                        .withHeaderBackground(R.mipmap.ic_launcher)
                        .withSelectionListEnabledForSingleProfile(false)
                        .addProfiles(
                                new ProfileDrawerItem().withName(user.getDisplayName())
                                        .withEmail(user.getEmail())
                                        .withIcon(R.drawable.ic_person_black_24dp)
                        )
                        .withOnAccountHeaderListener(new AccountHeader.OnAccountHeaderListener() {
                            @Override
                            public boolean onProfileChanged(View view,
                                                            IProfile profile,
                                                            boolean currentProfile) {
                                AuthUI.getInstance()
                                        .signOut(TeamListActivity.this)
                                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                                            @Override
                                            public void onComplete(@NonNull Task<Void> task) {
                                                // user is now signed out
                                            }
                                        });
                                return true;
                            }
                        })
                        .build();
            } else {
                header = getNavBarSignedOut();
            }
        else {
            // not signed in
            header = getNavBarSignedOut();
        }

        PrimaryDrawerItem item1 = new PrimaryDrawerItem().withIdentifier(1)
                .withName("R.string.drawer_item_home");
        mDrawer = new DrawerBuilder()
                .withActivity(this)
                .withToolbar(mToolbar)
                .withAccountHeader(header)
                .addDrawerItems(
                        item1,
                        new DividerDrawerItem(),
                        new SecondaryDrawerItem().withIdentifier(2)
                                .withName("R.string.drawer_item_settings1"),
                        new SecondaryDrawerItem().withName("R.string.drawer_item_setting2s")
                )
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        // do something with the clicked item :D
                        return true;
                    }
                })
                .build();

        mDrawer.setSelection(item1);
    }

    private AccountHeader getNavBarSignedOut() {
        AccountHeader header;// not signed in
        header = new AccountHeaderBuilder()
                .withActivity(this)
                .withHeaderBackground(R.mipmap.ic_launcher)
                .withSelectionListEnabledForSingleProfile(false)
                .addProfiles(
                        new ProfileDrawerItem().withName(getString(R.string.sign_in))
                                .withIcon(R.drawable.ic_person_black_24dp)
                )
                .withOnAccountHeaderListener(new AccountHeader.OnAccountHeaderListener() {
                    @Override
                    public boolean onProfileChanged(View view,
                                                    IProfile profile,
                                                    boolean currentProfile) {
                        // TODO: 09/06/2016 Fix sign in
                        startActivityForResult(
                                AuthUI.getInstance().createSignInIntentBuilder()
                                        .setLogo(R.drawable.launch_logo_image)
                                        .setProviders(
                                                Arrays.asList(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER)
                                                                      .build(),
                                                              new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER)
                                                                      .build(),
                                                              new AuthUI.IdpConfig.Builder(AuthUI.FACEBOOK_PROVIDER)
                                                                      .build(),
                                                              new AuthUI.IdpConfig.Builder(AuthUI.TWITTER_PROVIDER)
                                                                      .build()))
                                        .build(),
                                RC_SIGN_IN);
                        return true;
                    }
                })
                .build();
        return header;
    }
}