package net.slions.fxservice;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.appcompat.app.AlertDialog.Builder;


public class TileServiceAutoRotate extends TileService {

    private static final String TAG = "FxScreenRotationAuto:";


    @Override
    public void onCreate()
    {
        //Log.i(TAG, "onCreate");
        requestListeningState(this, new ComponentName(this, getClass()));
        super.onCreate();
        updateTileResources();
    }

    @Override
    public void onStartListening()
    {
        //Log.i(TAG, "onStartListening");
        updateTileResources();
        super.onStartListening();
    }

    @Override
    public void onStopListening()
    {
        //Log.i(TAG, "onStopListening");
        //updateTileResources();
        super.onStopListening();
    }


    @Override
    public void onTileRemoved()
    {
        //Log.i(TAG, "onTileRemoved");
        super.onTileRemoved();
    }

    @Override
    public void onTileAdded()
    {
        //Log.i(TAG, "onTileAdded");
        super.onTileAdded();
        updateTileResources();
    }


    @Override
    public void onClick()
    {
        //Log.i(TAG, "onClick");
        if (Settings.System.canWrite(this))
        {
            // We have permissions, just get the job done
            FxSettings.toggleScreenRotationAuto(this);
            updateTileResources();
        }
        else
        {
            // We don't have the proper permissions, ask the user to give it to us
            showPermissionDialog();
        }
        super.onClick();
    }

    private void updateTileResources()
    {
        // TODO: since we don't change icon or label most of that stuff is not needed
        if (this.getQsTile() != null)
        {
            Tile tile = this.getQsTile();
            tile.setLabel(getString(R.string.tile_service_name_screen_rotation_auto));
            tile.setIcon(Icon.createWithResource(getApplicationContext(), R.drawable.ic_screen_rotation));

            if (FxSettings.isScreenRotationAuto(this))
            {
                tile.setState(Tile.STATE_ACTIVE);
            }
            else
            {
                tile.setState(Tile.STATE_INACTIVE);
            }

            tile.updateTile();
        }
    }

    private void showPermissionDialog()
    {
        Builder builder = new Builder(this, R.style.AppTheme_AlertDialog);
        builder.setCancelable(true)
                .setIcon(R.drawable.ic_screen_rotation)
                .setTitle(R.string.app_name)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());

                builder.setMessage(R.string.screen_rotation_permission_alert_dialog_message);
                builder.setPositiveButton(R.string.settings, (dialog, which) ->
                        // Launch system UI to manage "write settings" permission for this application
                        startActivityAndCollapse(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                .setData(Uri.parse("package:" + getPackageName())))
                );

        showDialog(builder.create());
    }
}