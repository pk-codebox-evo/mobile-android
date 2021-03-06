/*
 * Copyright (C) 2003-2014 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.ui.social;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import com.squareup.picasso.Picasso;

import org.exoplatform.R;
import org.exoplatform.controller.social.ComposeMessageController;
import org.exoplatform.model.SocialSpaceInfo;
import org.exoplatform.utils.ExoConstants;
import org.exoplatform.utils.ExoDocumentUtils;
import org.exoplatform.utils.Log;
import org.exoplatform.utils.PhotoUtils;
import org.exoplatform.utils.SettingUtils;
import org.exoplatform.widget.AddPhotoDialog;
import org.exoplatform.widget.PostWaitingDialog;
import org.exoplatform.widget.RectangleImageView;
import org.exoplatform.widget.RemoveAttachedPhotoDialog;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat.OnRequestPermissionsResultCallback;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class ComposeMessageActivity extends Activity implements View.OnClickListener, OnRequestPermissionsResultCallback {

  private PostWaitingDialog            _progressDialog;

  private int                          composeType             = -1;

  private EditText                     composeEditText;

  private LinearLayout                 fileAttachWrap;

  private TextView                     postDestinationView;

  private ImageView                    postDestinationIcon;

  private Button                       sendButton;

  private Button                       cancelButton;

  private String                       comment;

  private String                       statusUpdate;

  private String                       cancelText;

  private String                       sendText;

  private ComposeMessageController     messageController;

  public static ComposeMessageActivity composeMessageActivity;

  private String                       sdcard_temp_dir         = null;

  private int                          currentPosition         = -1;

  private static final String          TAG                     = ComposeMessageActivity.class.getName();

  private static final int             SELECT_POST_DESTINATION = 10;

  private final String                 KEY_COMPOSE_TYPE        = "COMPOSE_TYPE";

  private final String                 KEY_CURRENT_POSITION    = "CURRENT_POSITION";

  private final String                 KEY_TEMP_IMAGE          = "TEMP_IMAGE";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.compose_message_layout);
    changeLanguage();
    initLayout();
    composeMessageActivity = this;
    if (savedInstanceState != null) {
      composeType = savedInstanceState.getInt(KEY_COMPOSE_TYPE);
      currentPosition = savedInstanceState.getInt(KEY_CURRENT_POSITION);
      String tmpImage = savedInstanceState.getString(KEY_TEMP_IMAGE, null);
      if (tmpImage != null) {
        addImageToMessage(new File(tmpImage));
      }
    } else {
      composeType = getIntent().getIntExtra(ExoConstants.COMPOSE_TYPE, composeType);
      if (composeType == ExoConstants.COMPOSE_COMMENT_TYPE) {
        currentPosition = getIntent().getIntExtra(ExoConstants.ACTIVITY_CURRENT_POSITION, currentPosition);
      }
    }
    setActivityTitle(composeType);
    messageController = new ComposeMessageController(this, composeType, _progressDialog);
    if (composeType == ExoConstants.COMPOSE_COMMENT_TYPE) {
      ((LinearLayout) postDestinationView.getParent()).setVisibility(View.GONE);
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    outState.putInt(KEY_COMPOSE_TYPE, composeType);
    outState.putInt(KEY_CURRENT_POSITION, currentPosition);
    outState.putString(KEY_TEMP_IMAGE, sdcard_temp_dir);
    super.onSaveInstanceState(outState);
  }

  @Override
  protected void onDestroy() {
    composeMessageActivity = null;
    super.onDestroy();
  }

  private void initLayout() {
    postDestinationView = (TextView) findViewById(R.id.post_destination_text_view);
    postDestinationIcon = (ImageView) findViewById(R.id.post_destination_image);
    composeEditText = (EditText) findViewById(R.id.compose_text_view);
    ScrollView textFieldScrollView = (ScrollView) findViewById(R.id.compose_textfield_scroll);
    textFieldScrollView.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
          InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
          mgr.showSoftInput(composeEditText, InputMethodManager.RESULT_UNCHANGED_SHOWN);
        }
        return false;
      }
    });

    fileAttachWrap = (LinearLayout) findViewById(R.id.compose_attach_file_wrap);
    sendButton = (Button) findViewById(R.id.compose_send_button);
    sendButton.setText(sendText);
    sendButton.setOnClickListener(this);
    cancelButton = (Button) findViewById(R.id.compose_cancel_button);
    cancelButton.setText(cancelText);
    cancelButton.setOnClickListener(this);
  }

  private void setActivityTitle(int compType) {
    if (compType == ExoConstants.COMPOSE_POST_TYPE) {
      setTitle(statusUpdate);
    } else {
      setTitle(comment);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.composer, menu);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuItem attach = menu.findItem(R.id.menu_compose_attach);
    if (attach != null) {
      if (composeType == ExoConstants.COMPOSE_POST_TYPE) {
        // show the attach photo menu item
        attach.setVisible(true);
      } else if (composeType == ExoConstants.COMPOSE_COMMENT_TYPE) {
        // hide the attach photo menu item
        attach.setVisible(false);
      }
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_compose_attach:
      new AddPhotoDialog(this, messageController).show();
      break;
    }
    return true;
  }

  @SuppressLint("Override")
  @Override
  public void onRequestPermissionsResult(int reqCode, String[] permissions, int[] results) {
    if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
      // permission granted
      switch (reqCode) {
      case ExoConstants.REQUEST_TAKE_PICTURE_WITH_CAMERA:
        messageController.initCamera();
        break;
      case ExoConstants.REQUEST_PICK_IMAGE_FROM_GALLERY:
        PhotoUtils.pickPhotoForActivity(this);
        break;
      default:
        break;
      }
    } else {
      // permission denied
      if (ExoDocumentUtils.shouldDisplayExplanation(this, ExoConstants.REQUEST_PICK_IMAGE_FROM_GALLERY)
          || ExoDocumentUtils.shouldDisplayExplanation(this, ExoConstants.REQUEST_TAKE_PICTURE_WITH_CAMERA)) {
        PhotoUtils.alertNeedStoragePermission(this);
      } else {
        Toast.makeText(this, R.string.PermissionStorageDeniedToast, Toast.LENGTH_LONG).show();
      }
    }
  }

  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);
    if (resultCode == RESULT_OK) {
      switch (requestCode) {
      // Add image after capturing photo from camera
      case ExoConstants.REQUEST_TAKE_PICTURE_WITH_CAMERA:
        String sdcard_dir = messageController.getSdCardTempDir();
        if (sdcard_dir != null) {
            File file = new File(sdcard_dir);
            addImageToMessage(file);
        } else {
            Toast.makeText(this, R.string.AttachPhotoError, Toast.LENGTH_LONG).show();
        }
        break;

      // Get the pick image action result from native photo album and send
      // it to SelectedImageActivity class
      case ExoConstants.REQUEST_PICK_IMAGE_FROM_GALLERY:
        Intent intent2 = new Intent(this, SelectedImageActivity.class);
        Uri uri = intent.getData();
        intent.putExtra(ExoConstants.SELECTED_IMAGE_MODE, 2);
        intent2.setData(uri);
        if (intent.getExtras() != null) {
          intent2.putExtras(intent.getExtras());
        }

        startActivity(intent2);
        break;

      // Get the destination of the post from the SpaceSelectorActivity
      case SELECT_POST_DESTINATION:
        SocialSpaceInfo space = intent.getParcelableExtra(SpaceSelectorActivity.SELECTED_SPACE);

        if (space != null) {
          messageController.setPostDestination(space);
          postDestinationView.setText(space.displayName);
          Picasso.with(this).load(space.avatarUrl).placeholder(R.drawable.icon_space_default).into(postDestinationIcon);
        } else {
          messageController.setPostDestination(null);
          postDestinationView.setText(R.string.Public);
          Picasso.with(this).load(R.drawable.icon_post_public).into(postDestinationIcon);
        }
        break;
      }
    } else {
      // TODO handle failure of startActivityForResult
    }
    /*
     * Set default language to our application setting language
     */
    SettingUtils.setDefaultLanguage(this);
  }

  public static void addImageToMessage(File file) {
    try {
      final String filePath = file.getAbsolutePath();
      composeMessageActivity.sdcard_temp_dir = filePath;
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize = 4;
      options.inPurgeable = true;
      options.inInputShareable = true;
      FileInputStream fis = new FileInputStream(file);
      Bitmap bitmap = BitmapFactory.decodeStream(fis, null, options);
      fis.close();
      bitmap = PhotoUtils.resizeImageBitmap(composeMessageActivity, bitmap);
      bitmap = ExoDocumentUtils.rotateBitmapToNormal(filePath, bitmap);

      RectangleImageView image = new RectangleImageView(composeMessageActivity);
      image.setPadding(1, 1, 1, 1);
      image.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
      image.setImageBitmap(bitmap);
      image.setOnClickListener(new OnClickListener() {

        public void onClick(View v) {
          Intent intent = new Intent(composeMessageActivity, SelectedImageActivity.class);
          intent.putExtra(ExoConstants.SELECTED_IMAGE_MODE, 1);
          intent.putExtra(ExoConstants.SELECTED_IMAGE_EXTRA, filePath);
          composeMessageActivity.startActivity(intent);
        }
      });
      image.setOnLongClickListener(new OnLongClickListener() {

        @Override
        public boolean onLongClick(View v) {
          new RemoveAttachedPhotoDialog(composeMessageActivity).show();
          return true;
        }
      });
      LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      composeMessageActivity.fileAttachWrap.removeAllViews();
      composeMessageActivity.fileAttachWrap.addView(image, params);
    } catch (IOException e) {
      Log.e(TAG, "Error when adding image to message", e);
    }
  }

  public static void removeImageFromMessage() {
    if (composeMessageActivity != null) {
      composeMessageActivity.fileAttachWrap.removeAllViews();
      composeMessageActivity.sdcard_temp_dir = null;
    }
  }

  @Override
  public void finish() {
    if (_progressDialog != null) {
      _progressDialog.dismiss();
    }
    super.finish();
  }

  @Override
  public void onBackPressed() {
    finish();
  }

  @Override
  public void onClick(View view) {

    if (view.equals(sendButton)) {
      String composeMessage = composeEditText.getText().toString();
      messageController.onSendMessage(composeMessage, sdcard_temp_dir, currentPosition);
    }
    if (view.equals(cancelButton)) {
      finish();
    }
  }

  public void openSpaceSelectionActivity(View v) {
    // Open the Space Selector Activity and expect a result
    Intent selectSpace = new Intent(this, SpaceSelectorActivity.class);
    startActivityForResult(selectSpace, SELECT_POST_DESTINATION);
  }

  private void changeLanguage() {
    Resources resource = getResources();
    comment = resource.getString(R.string.Comment);
    statusUpdate = resource.getString(R.string.StatusUpdate);
    sendText = resource.getString(R.string.Send);
    cancelText = resource.getString(R.string.Cancel);
  }
}
