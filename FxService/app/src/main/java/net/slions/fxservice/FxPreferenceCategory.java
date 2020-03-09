package net.slions.fxservice;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

// Used to enable multiple line summary
// See: https://stackoverflow.com/questions/6729484/android-preference-summary-how-to-set-3-lines-in-summary
public class FxPreferenceCategory extends PreferenceCategory
{

    public FxPreferenceCategory(Context ctx, AttributeSet attrs, int defStyle)
    {
        super(ctx, attrs, defStyle);
    }

    public FxPreferenceCategory(Context ctx, AttributeSet attrs)
    {
        super(ctx, attrs);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder)
    {
        super.onBindViewHolder(holder);
        TextView summary= (TextView)holder.findViewById(android.R.id.summary);
        if (summary != null)
        {
            // Enable multiple line support
            summary.setSingleLine(false);
            summary.setMaxLines(10); // Just need to be high enough I guess
        }
    }

}
