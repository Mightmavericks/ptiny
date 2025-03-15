package com.example.protegotinyever.act;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;
import com.example.protegotinyever.R;
import com.example.protegotinyever.adapt.OnboardingAdapter;
import com.example.protegotinyever.model.OnboardingItem;
import com.example.protegotinyever.util.SessionManager;
import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {
    private OnboardingAdapter onboardingAdapter;
    private LinearLayout indicatorLayout;
    private ViewPager2 onboardingViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if onboarding was completed before
        if (SessionManager.getInstance(this).isOnboardingCompleted()) {
            startLoginActivity();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        indicatorLayout = findViewById(R.id.indicatorLayout);
        onboardingViewPager = findViewById(R.id.onboardingViewPager);

        setupOnboardingItems();
        setupIndicators();
        setCurrentIndicator(0);

        onboardingViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                setCurrentIndicator(position);
            }
        });

        findViewById(R.id.buttonNext).setOnClickListener(view -> {
            if (onboardingViewPager.getCurrentItem() + 1 < onboardingAdapter.getItemCount()) {
                onboardingViewPager.setCurrentItem(onboardingViewPager.getCurrentItem() + 1);
            } else {
                finishOnboarding();
            }
        });

        findViewById(R.id.buttonSkip).setOnClickListener(view -> finishOnboarding());
    }

    private void setupOnboardingItems() {
        List<OnboardingItem> onboardingItems = new ArrayList<>();

        OnboardingItem secureChat = new OnboardingItem();
        secureChat.setTitle("Secure P2P Chat");
        secureChat.setDescription("Enjoy end-to-end encrypted messaging with direct peer-to-peer connections. Your messages never pass through servers.");
        secureChat.setImage(R.drawable.ic_secure_chat);

        OnboardingItem fileSharing = new OnboardingItem();
        fileSharing.setTitle("Fast File Sharing");
        fileSharing.setDescription("Share files directly with your contacts. 200MB size limit, no compression, and maximum speed through P2P transfer.");
        fileSharing.setImage(R.drawable.ic_file_sharing);

        OnboardingItem privacy = new OnboardingItem();
        privacy.setTitle("Privacy First");
        privacy.setDescription("Your data stays on your device. We don't store your messages or files on any servers. Complete privacy guaranteed.");
        privacy.setImage(R.drawable.ic_privacy);

        onboardingItems.add(secureChat);
        onboardingItems.add(fileSharing);
        onboardingItems.add(privacy);

        onboardingAdapter = new OnboardingAdapter(onboardingItems);
        onboardingViewPager.setAdapter(onboardingAdapter);
    }

    private void setupIndicators() {
        ImageView[] indicators = new ImageView[onboardingAdapter.getItemCount()];
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layoutParams.setMargins(8, 0, 8, 0);

        for (int i = 0; i < indicators.length; i++) {
            indicators[i] = new ImageView(getApplicationContext());
            indicators[i].setImageDrawable(ContextCompat.getDrawable(
                    getApplicationContext(),
                    R.drawable.indicator_inactive
            ));
            indicators[i].setLayoutParams(layoutParams);
            indicatorLayout.addView(indicators[i]);
        }
    }

    private void setCurrentIndicator(int position) {
        int childCount = indicatorLayout.getChildCount();
        for (int i = 0; i < childCount; i++) {
            ImageView imageView = (ImageView) indicatorLayout.getChildAt(i);
            if (i == position) {
                imageView.setImageDrawable(
                        ContextCompat.getDrawable(getApplicationContext(), R.drawable.indicator_active)
                );
            } else {
                imageView.setImageDrawable(
                        ContextCompat.getDrawable(getApplicationContext(), R.drawable.indicator_inactive)
                );
            }
        }

        // Update button text for last page
        if (position == onboardingAdapter.getItemCount() - 1) {
            findViewById(R.id.buttonNext).setContentDescription("Get Started");
        } else {
            findViewById(R.id.buttonNext).setContentDescription("Next");
        }
    }

    private void finishOnboarding() {
        SessionManager.getInstance(this).setOnboardingCompleted(true);
        startLoginActivity();
    }

    private void startLoginActivity() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
} 