package com.lunartag.app.ui.logs;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.lunartag.app.MainActivity;
import com.lunartag.app.R;

public class LogFragment extends Fragment {

    private TextView textLogs;
    private ScrollView scrollView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_logs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textLogs = view.findViewById(R.id.text_full_logs);
        scrollView = view.findViewById(R.id.scroll_view_logs);

        // Retrieve the full history from MainActivity (Central Brain)
        if (getActivity() instanceof MainActivity) {
            String history = ((MainActivity) getActivity()).getGlobalLogs();
            textLogs.setText(history);
            scrollToBottom();
        }
    }

    /**
     * Called by MainActivity when a new log arrives while this screen is visible.
     */
    public void appendLog(String message) {
        if (textLogs != null) {
            textLogs.append(message + "\n");
            scrollToBottom();
        }
    }

    private void scrollToBottom() {
        if (scrollView != null) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    scrollView.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }
              }
