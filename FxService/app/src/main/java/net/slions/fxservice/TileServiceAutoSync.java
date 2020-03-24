package net.slions.fxservice;

import android.content.ComponentName;
import android.graphics.drawable.Icon;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;


public class TileServiceAutoSync extends TileService {

    private static final int PERMISSION_DIALOG = 42;
    private static final int SETTING_NOT_FOUND_DIALOG = 24;
    private static final String TAG = "FxAutoSync:";


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
        toggleAutoSync();
        updateTileResources();
        super.onClick();
    }

    private void updateTileResources()
    {
        if (this.getQsTile() != null)
        {
            Tile tile = this.getQsTile();
            //tile.setLabel(getString(R.string.tile_service_name_adaptive_brightness));
            //tile.setIcon(Icon.createWithResource(getApplicationContext(), R.drawable.ic_sync_24px));
            if (getContentResolver().getMasterSyncAutomatically())
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

    private void toggleAutoSync()
    {
        getContentResolver().setMasterSyncAutomatically(!getContentResolver().getMasterSyncAutomatically());
    }


}