package com.example.contactlist;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.opencsv.CSVReader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static android.content.ContentValues.TAG;

public class MainActivity extends AppCompatActivity {
    RecyclerView recyclerView;
    ArrayList<ContactModel> arrayList = new ArrayList<ContactModel>();
    HashSet<ContactModel> contactsSet;
    MainAdapter adapter;
    Database database;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recycler_view);
        database = new Database(this, "contacts.sqlite", null, 1);
        database.queryData("CREATE TABLE IF NOT EXISTS Contacts(Id INTEGER PRIMARY KEY AUTOINCREMENT, Name String, Number String)");
        // Kiem tra quyen truy cap danh ba
        checkPermission();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_contact_app, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.merge:
                this.arrayList = merge();
                adapter = new MainAdapter(MainActivity.this,this, arrayList);
                recyclerView.setAdapter(adapter);
                Toast.makeText(MainActivity.this, "Merged", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.ReadContacts:
                readContactsAndSync();
                Toast.makeText(MainActivity.this, "Read contacts from phone successfully!", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.ExportCSV:
                if (Build.VERSION.SDK_INT >= 30){
                    if (!Environment.isExternalStorageManager()){
                        Intent getpermission = new Intent();
                        getpermission.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivity(getpermission);
                    }
                }
                exportCSV();
                Toast.makeText(MainActivity.this, "Exported", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.ImportCSV:
                importCSV();
                Toast.makeText(MainActivity.this, "Imported", Toast.LENGTH_SHORT).show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public ArrayList<ContactModel> merge()
    {
        ArrayList<ContactModel> duplicateContacts = findDuplicateContactByPhoneNumber();

        for (ContactModel contactModel : duplicateContacts) {
            ArrayList<ContactModel> arr = getContactByPhoneNumber(contactModel.getNumber());
            for (ContactModel c : arr) {
                if (contactModel.getId() != c.getId()) {
                    deleteContact(c.getId());
                }
            }
        }

        return getContactList();
    }

    public void readContactsAndSync()
    {
        arrayList.clear();
        readContactListFromPhone();
        if (!isEmptyDatabase(database)) {                       // Check db trong k
                    database.queryData("DELETE FROM Contacts");     // Co du lieu thi xoa het
        }

        // Nap lai vao db data tu danh ba trong may
        for (ContactModel contactModel : arrayList) {
            addContact(contactModel);
        }

        arrayList = getContactList();
        adapter = new MainAdapter(MainActivity.this,this, arrayList);
        recyclerView.setAdapter(adapter);
    }

    public void exportCSV(){

        File exportDir = new File(Environment.getExternalStorageDirectory(), "");
        if (!exportDir.exists())
        {
            exportDir.mkdirs();
        }

        File file = new File(exportDir, "contacts.csv");
        try
        {
            file.createNewFile();
            CSVWriter csvWrite = new CSVWriter(new FileWriter(file));

            ArrayList<ContactModel> contactList =  new ArrayList<>();
            Cursor cursor = database.getData("SELECT * FROM Contacts");
            while (cursor.moveToNext()) {
                String arrStr[] ={cursor.getString(0),cursor.getString(1), cursor.getString(2)};
                csvWrite.writeNext(arrStr);
            }
            csvWrite.close();
            cursor.close();
        }
        catch(Exception sqlEx)
        {
            Log.e("MainActivity", sqlEx.getMessage(), sqlEx);
        }
    }

    public void importCSV(){
        try {
            arrayList.clear();
            if (!isEmptyDatabase(database)) {                       // Check db trong k
                database.queryData("DELETE FROM Contacts");     // Co du lieu thi xoa het
            }


            File csvfile = new File(Environment.getExternalStorageDirectory() + "/contacts.csv");
            CSVReader reader = new CSVReader(new FileReader(csvfile.getAbsolutePath()));
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                // nextLine[] is an array of values from the line
                ContactModel contactModel = new ContactModel(Integer.parseInt(nextLine[0]), nextLine[1], nextLine[2]);
                addContact(contactModel);
            }
        } catch (IOException e) {
            Log.e("CSV Import Ex", e.toString());
        }

        arrayList = getContactList();
        adapter = new MainAdapter(MainActivity.this,this, arrayList);
        recyclerView.setAdapter(adapter);
    }

    public boolean isEmptyDatabase(Database db) {
        Cursor contactList = database.getData("SELECT COUNT(*) FROM Contacts");
        contactList.moveToFirst();
        int icount = contactList.getInt(0);
        Log.e("Table count", String.valueOf(icount));
        if(icount > 0)
            return false;
        return  true;
    }

    private void readContactListFromPhone() {
        Uri uri = ContactsContract.Contacts.CONTENT_URI;

        // sort t??ng d???n theo t??n A-Z
        String sort = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC";
        Cursor cursor = getContentResolver().query(
                uri,null,null,null, sort
        );

        // So contact trong danh ba lon hon 0
        if (cursor.getCount() > 0) {
            while(cursor.moveToNext()){
                //Get contact id
                List<String> nameContact = new ArrayList<String>();
                String id = cursor.getString(cursor.getColumnIndex(
                        ContactsContract.Contacts._ID
                ));
                //Get contact name
                String name = cursor.getString(cursor.getColumnIndex(
                        ContactsContract.Contacts.DISPLAY_NAME
                ));

                // T???o phone uri
                Uri uriPhone = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
                // Tao selection uri
                String selection = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " =?";

                Cursor phoneCursor = getContentResolver().query(
                        uriPhone, null, selection,new String[]{id}, null
                );

                if (phoneCursor.moveToNext()){
                    String number = phoneCursor.getString(phoneCursor.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    ));

                    ContactModel model = new ContactModel();
                    model.setName(name);
                    model.setNumber(number);
                    arrayList.add(model);

                    phoneCursor.close();
                }
            }
            cursor.close();

        }
        //set data vao list view
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MainAdapter(MainActivity.this,this, arrayList);
        recyclerView.setAdapter(adapter);
    }

    private ArrayList<ContactModel> getContactList() {
        ArrayList<ContactModel> contactList =  new ArrayList<>();
        Cursor cursor = database.getData("SELECT * FROM Contacts");
        while (cursor.moveToNext()) {
            int id = cursor.getInt(0);
            String name = cursor.getString(1);
            String phoneNumber = cursor.getString(2);

            contactList.add(new ContactModel(id, name, phoneNumber));
        }
        adapter.notifyDataSetChanged();

        return contactList;
    }

    private ArrayList<ContactModel> getContactByPhoneNumber(String phoneNumber) {
        ArrayList<ContactModel> contactList = new ArrayList<>();
        Cursor cursor = database.getData("SELECT * FROM Contacts WHERE Number = '" + phoneNumber +"'");
        while (cursor.moveToNext()) {
            int id = cursor.getInt(0);
            String name = cursor.getString(1);

            contactList.add(new ContactModel(id, name, phoneNumber));
        }

        return contactList;
    }

    private ArrayList<ContactModel> findDuplicateContactByPhoneNumber() {
        ArrayList<ContactModel> duplicateContact = new ArrayList<>();
        Cursor cursor = database.getData("SELECT Id, Name, Number, COUNT(Number) FROM Contacts GROUP BY Number HAVING COUNT(Number) > 1");
        while (cursor.moveToNext()) {
            int id = cursor.getInt(0);
            String name = cursor.getString(1);
            String phoneNumber = cursor.getString(2);

            duplicateContact.add(new ContactModel(id, name, phoneNumber));
        }

        return duplicateContact;
    }

    private void addContact(ContactModel contactModel) {
        database.queryData("INSERT INTO Contacts VALUES(null, '" + contactModel.getName() + "', '" + contactModel.getNumber() + "')");
    }

    private void deleteContact(int id) {
        database.queryData("DELETE FROM Contacts WHERE Id = " + id);
    }

    private  void checkPermission(){
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED)
            // Yeu cau quyen truy cap danh ba neu khong co quyen
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_CONTACTS}, 100);
        else {
            readContactListFromPhone();   // cho nay la lay data tu danh ba trong may phong cho truong hop chay app lan dau
            arrayList = getContactList();
            adapter = new MainAdapter(MainActivity.this,this, arrayList);
            recyclerView.setAdapter(adapter);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0]
            == PackageManager.PERMISSION_GRANTED){
            // khi duoc cap quyen lap tuc get contact list
            readContactListFromPhone();
        } else {
            // khi bi tu choi quyen truy cap danh ba
            Toast.makeText(MainActivity.this, "Permission Dennied.", Toast.LENGTH_SHORT).show();
            checkPermission();
        }
    }
}