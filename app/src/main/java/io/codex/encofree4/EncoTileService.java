package io.codex.encofree4;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class EncoTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setLabel(getString(R.string.tile_label));
            tile.setState(connectedState());
            tile.updateTile();
        }
    }

    @Override
    public void onClick() {
        openControlPanel();
    }

    private void openControlPanel() {
        Intent intent = new Intent(this, ControlActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        startActivityAndCollapse(pendingIntent);
    }

    private int connectedState() {
        // Opening the panel uses the session shared with desktop widgets.
        return Tile.STATE_INACTIVE;
    }
}
