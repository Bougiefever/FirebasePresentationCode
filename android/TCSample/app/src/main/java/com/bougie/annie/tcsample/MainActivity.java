package com.bougie.annie.tcsample;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.appcompat.*;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.ads.AdView;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity
        implements GoogleApiClient.OnConnectionFailedListener {

    public static final String ANONYMOUS = "anonymous";
    public static final int REQUEST_INVITE = 1;

    private int _number_of_items = 5;

    private ArrayList _arrayList;
    private ArrayAdapter _arrayAdapter;
    private EditText _inputItem;
    private SharedPreferences _sharedPreferences;
    private String _username;
    private String _photoUrl;
    private GoogleApiClient _googleApiClient;

    private static final String TAG = "MainActivity";

    //Firebase declarations
    private FirebaseAuth _firebaseAuth;
    private FirebaseUser _firebaseUser;
    private FirebaseDatabase _firebaseDatabase;
    private DatabaseReference _firebaseDatabaseReference;
    private FirebaseRemoteConfig _firebaseRemoteConfig;
    private ChildEventListener _childEventListener;
    private FirebaseAnalytics _firebaseAnalytics;
    private AdView _adView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // Set default username is anonymous.
        _username = ANONYMOUS;

        _firebaseAuth = FirebaseAuth.getInstance();
        _firebaseUser = _firebaseAuth.getCurrentUser();

        if (_firebaseUser == null) {
            // Not signed in, launch the Sign In activity
            startActivity(new Intent(this, SigninActivity.class));
            finish();
            return;
        } else {
            _username = _firebaseUser.getDisplayName();
            if (_firebaseUser.getPhotoUrl() != null) {
                _photoUrl = _firebaseUser.getPhotoUrl().toString();
            }
        }

        _googleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API)
                .build();

        _firebaseDatabase = FirebaseDatabase.getInstance();
        _firebaseDatabaseReference = _firebaseDatabase.getReference();
        _childEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                ShoppingItem shoppingItem = dataSnapshot.getValue(ShoppingItem.class);
                addNewShoppingListItem(shoppingItem.item);
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                ShoppingItem shoppingItem = dataSnapshot.getValue(ShoppingItem.class);
                removeShoppingListItem(shoppingItem.item);
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };


        final ListView listView = (ListView) findViewById(R.id.listv);
        String[] items = {};
        _arrayList = new ArrayList(Arrays.asList(items));
        _arrayAdapter = new ArrayAdapter(this, R.layout.list_item, R.id.txtItem, _arrayList);
        listView.setAdapter(_arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                AlertDialog.Builder adb = new AlertDialog.Builder(MainActivity.this);
                adb.setTitle("Remove?");
                final String itemText = listView.getItemAtPosition(i).toString();
                adb.setMessage("Are you sure you want to remove " + itemText);
                adb.setNegativeButton("No", null);
                adb.setPositiveButton("Yes", new AlertDialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //removeShoppingListItem(itemText);
                        _firebaseDatabaseReference.child("items")
                                .orderByChild("item")
                                .equalTo(itemText)
                                .addListenerForSingleValueEvent(
                                        new ValueEventListener() {
                                            @Override
                                            public void onDataChange(DataSnapshot dataSnapshot) {
                                                for (DataSnapshot child : dataSnapshot.getChildren()) {
                                                    String key = child.getKey();
                                                    DatabaseReference removeRef
                                                            = _firebaseDatabase.getReference("items/" + key);
                                                    removeRef.removeValue();
                                                }
                                            }

                                            @Override
                                            public void onCancelled(DatabaseError databaseError) {

                                            }
                                        }
                                );
                    }
                });
                adb.show();
            }
        });

        _inputItem = (EditText) findViewById(R.id.txtInput);
        Button addButton = (Button) findViewById(R.id.btnAddItem);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String newItem = _inputItem.getText().toString();
                //addNewShoppingListItem(newItem);
                ShoppingItem shoppingItem = new ShoppingItem(_firebaseUser.getDisplayName(), newItem);
                _firebaseDatabaseReference.child("items").push().setValue(shoppingItem);
                _inputItem.setText("");
            }
        });

        _firebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings settings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build();
        _firebaseRemoteConfig.setConfigSettings(settings);
        _firebaseRemoteConfig.setDefaults(R.xml.remote_config_defaults);
        _number_of_items = (int) _firebaseRemoteConfig.getLong("number_of_items");
        listenForShoppingItemsChanges();
    }

    private void addNewShoppingListItem(String item) {
        _arrayList.add(item);
        _arrayAdapter.notifyDataSetChanged();
    }

    private void removeShoppingListItem(String item) {
        int position = _arrayList.indexOf(item);
        _arrayList.remove(position);
        _arrayAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                _firebaseAuth.signOut();
                Auth.GoogleSignInApi.signOut(_googleApiClient);
                _username = ANONYMOUS;
                startActivity(new Intent(this, SigninActivity.class));
                return true;
            case R.id.fresh_config_menu:

                fetchConfigValues();
                return true;
            case R.id.invite_menu:
                sendInvitation();

                return true;
            case R.id.crash_menu:
                crashApp();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show();
    }


    private void sendInvitation() {

    }

    private void crashApp() {
        try {
            throw new RuntimeException("Testing crash reporting");
        } catch (Exception exception) {
            //todo crash reporting to Firebase
            throw exception;
        }
    }

    private void fetchConfigValues() {

    }

    private void listenForShoppingItemsChanges() {
        _firebaseDatabaseReference.child("items")
                .limitToFirst(_number_of_items)
                .addChildEventListener(
                _childEventListener
        );
    }
}