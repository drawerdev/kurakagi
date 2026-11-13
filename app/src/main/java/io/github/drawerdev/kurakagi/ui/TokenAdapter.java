package io.github.drawerdev.kurakagi.ui;

import android.content.Context;
import android.content.Intent;
import android.text.ClipboardManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import io.github.drawerdev.kurakagi.R;
import io.github.drawerdev.kurakagi.core.Base32;
import io.github.drawerdev.kurakagi.core.TimeSyncManager;
import io.github.drawerdev.kurakagi.core.TotpCore;
import io.github.drawerdev.kurakagi.storage.Account;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * トークン一覧用のカスタムモダンアダプター。
 * 3連コードの展開・折りたたみ、ワンタップコピー、詳細画面遷移をサポート。
 */
public class TokenAdapter extends BaseAdapter {

    private final Context context;
    private final LayoutInflater inflater;
    private List<Account> accountList = new ArrayList<Account>();
    private final Set<String> expandedAccountIds = new HashSet<String>();
    private final TimeSyncManager timeSyncManager;

    public TokenAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.timeSyncManager = TimeSyncManager.getInstance(context);
    }

    public void setAccounts(List<Account> accounts) {
        this.accountList = (accounts != null) ? accounts : new ArrayList<Account>();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return accountList.size();
    }

    @Override
    public Account getItem(int position) {
        return accountList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_token, parent, false);
            holder = new ViewHolder();
            holder.cardRoot = convertView.findViewById(R.id.card_token_root);
            holder.tvIssuer = (TextView) convertView.findViewById(R.id.tv_item_issuer);
            holder.tvLabel = (TextView) convertView.findViewById(R.id.tv_item_label);
            holder.tvCode = (TextView) convertView.findViewById(R.id.tv_item_code);
            holder.tvTapToCopy = (TextView) convertView.findViewById(R.id.tv_tap_to_copy);
            holder.tvRemaining = (TextView) convertView.findViewById(R.id.tv_item_remaining);
            holder.btnDetail = (Button) convertView.findViewById(R.id.btn_item_detail);
            holder.layoutMainCode = convertView.findViewById(R.id.layout_main_code);
            holder.btnToggleTriplet = (Button) convertView.findViewById(R.id.btn_toggle_triplet);
            holder.layoutTriplet = convertView.findViewById(R.id.layout_triplet_container);
            holder.viewTripletDivider = convertView.findViewById(R.id.view_triplet_divider);
            holder.tvTripletHint = (TextView) convertView.findViewById(R.id.tv_triplet_hint);
            holder.tvSlotPrevLabel = (TextView) convertView.findViewById(R.id.tv_slot_prev_label);
            holder.tvSlotCurrLabel = (TextView) convertView.findViewById(R.id.tv_slot_curr_label);
            holder.tvSlotNextLabel = (TextView) convertView.findViewById(R.id.tv_slot_next_label);
            holder.tvSlotPrev = (TextView) convertView.findViewById(R.id.tv_slot_prev_code);
            holder.tvSlotCurr = (TextView) convertView.findViewById(R.id.tv_slot_curr_code);
            holder.tvSlotNext = (TextView) convertView.findViewById(R.id.tv_slot_next_code);
            holder.slotPrev = convertView.findViewById(R.id.slot_prev);
            holder.slotCurr = convertView.findViewById(R.id.slot_curr);
            holder.slotNext = convertView.findViewById(R.id.slot_next);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Account account = getItem(position);
        final String accountId = account.getId();

        // 発行元とアカウント名
        String issuer = account.getIssuer();
        if (issuer == null || issuer.length() == 0) {
            issuer = context.getString(R.string.default_issuer_label);
        }
        holder.tvIssuer.setText(issuer);
        holder.tvLabel.setText(account.getLabel());

        // TOTP 計算
        long currentTimeMs = timeSyncManager.getCurrentTimeMillis();
        int period = account.getPeriodSeconds() > 0 ? account.getPeriodSeconds() : 30;
        int digits = account.getDigits() > 0 ? account.getDigits() : 6;
        String algo = account.getAlgorithm();

        byte[] secretBytes;
        try {
            secretBytes = Base32.decode(account.getSecret());
        } catch (Exception e) {
            secretBytes = new byte[0];
        }

        String p = "------";
        String c = context.getString(R.string.code_error);
        String n = "------";
        boolean hasError = true;

        if (secretBytes.length > 0) {
            try {
                String[] triplet = TotpCore.generateTotpTriplet(secretBytes, currentTimeMs, period, digits, algo);
                p = formatCode(triplet[0]);
                c = formatCode(triplet[1]);
                n = formatCode(triplet[2]);
                hasError = false;
            } catch (Exception e) {
                p = "------";
                c = context.getString(R.string.code_error);
                n = "------";
                hasError = true;
            }
        }

        final String prevCode = p;
        final String currentCode = c;
        final String nextCode = n;

        holder.tvCode.setText(currentCode);
        holder.tvSlotPrev.setText(prevCode);
        holder.tvSlotCurr.setText(currentCode);
        holder.tvSlotNext.setText(nextCode);

        // 残り秒数計算
        long currentSec = currentTimeMs / 1000L;
        int remainingSec = (int) (period - (currentSec % period));
        holder.tvRemaining.setText(context.getString(R.string.time_remaining_format, remainingSec));

        // メインコードタップでコピー
        holder.layoutMainCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyToClipboard(account.getLabel(), currentCode.replace(" ", ""));
            }
        });

        // 3連コード各スロットタップでコピー
        holder.slotPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyToClipboard(context.getString(R.string.slot_prev_suffix, account.getLabel()), prevCode.replace(" ", ""));
            }
        });
        holder.slotCurr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyToClipboard(account.getLabel(), currentCode.replace(" ", ""));
            }
        });
        holder.slotNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyToClipboard(context.getString(R.string.slot_next_suffix, account.getLabel()), nextCode.replace(" ", ""));
            }
        });

        // アコーディオン展開状態の反映
        final boolean isExpanded = expandedAccountIds.contains(accountId);
        holder.layoutTriplet.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.btnToggleTriplet.setText(context.getString(
                isExpanded ? R.string.btn_triplet_collapse : R.string.btn_triplet_expand));

        // テーマカラー・背景の適用
        applyItemTheme(holder, isExpanded, remainingSec, hasError);

        holder.btnToggleTriplet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isExpanded) {
                    expandedAccountIds.remove(accountId);
                } else {
                    expandedAccountIds.add(accountId);
                }
                notifyDataSetChanged();
            }
        });

        // 詳細・再バックアップ画面へ遷移
        holder.btnDetail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, TokenDetailActivity.class);
                intent.putExtra(TokenDetailActivity.EXTRA_ACCOUNT_ID, accountId);
                context.startActivity(intent);
            }
        });

        return convertView;
    }

    private void applyItemTheme(ViewHolder holder, boolean isExpanded, int remainingSec, boolean hasError) {
        setBackgroundPreservingPadding(holder.cardRoot,
                isExpanded ? ThemeManager.getCardHighlightBgRes(context) : ThemeManager.getCardBgRes(context));

        holder.tvIssuer.setTextColor(ThemeManager.getAccentPrimaryColor(context));
        holder.tvLabel.setTextColor(ThemeManager.getTextPrimaryColor(context));

        setBackgroundPreservingPadding(holder.btnDetail, ThemeManager.getBtnCompactBgRes(context));
        holder.btnDetail.setTextColor(ThemeManager.getTextSecondaryColor(context));

        holder.tvCode.setTextColor(hasError
                ? ThemeManager.getTimerDangerColor(context)
                : ThemeManager.getAccentPrimaryColor(context));
        holder.tvTapToCopy.setTextColor(ThemeManager.getTextMutedColor(context));

        holder.tvRemaining.setTextColor(remainingSec <= 5
                ? ThemeManager.getTimerDangerColor(context)
                : ThemeManager.getTimerSafeColor(context));

        setBackgroundPreservingPadding(holder.btnToggleTriplet, ThemeManager.getBtnCompactBgRes(context));
        holder.btnToggleTriplet.setTextColor(ThemeManager.getAccentPrimaryColor(context));

        holder.viewTripletDivider.setBackgroundColor(ThemeManager.getDividerColor(context));
        holder.tvTripletHint.setTextColor(ThemeManager.getTimerWarningColor(context));

        setBackgroundPreservingPadding(holder.slotPrev, ThemeManager.getSlotBoxBgRes(context));
        holder.tvSlotPrevLabel.setTextColor(ThemeManager.getTextMutedColor(context));
        holder.tvSlotPrev.setTextColor(ThemeManager.getTextSecondaryColor(context));

        setBackgroundPreservingPadding(holder.slotCurr, ThemeManager.getSlotBoxActiveBgRes(context));
        holder.tvSlotCurrLabel.setTextColor(ThemeManager.getAccentPrimaryColor(context));
        holder.tvSlotCurr.setTextColor(ThemeManager.getAccentPrimaryColor(context));

        setBackgroundPreservingPadding(holder.slotNext, ThemeManager.getSlotBoxBgRes(context));
        holder.tvSlotNextLabel.setTextColor(ThemeManager.getTextMutedColor(context));
        holder.tvSlotNext.setTextColor(ThemeManager.getTextSecondaryColor(context));
    }

    private static void setBackgroundPreservingPadding(View view, int resId) {
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();
        view.setBackgroundResource(resId);
        view.setPadding(left, top, right, bottom);
    }

    private String formatCode(String rawCode) {
        if (rawCode == null) return "------";
        if (rawCode.length() == 6) {
            return rawCode.substring(0, 3) + " " + rawCode.substring(3);
        } else if (rawCode.length() == 8) {
            return rawCode.substring(0, 4) + " " + rawCode.substring(4);
        }
        return rawCode;
    }

    private void copyToClipboard(String label, String code) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setText(code);
            Toast.makeText(context, context.getString(R.string.toast_code_copied_format, label, code), Toast.LENGTH_SHORT).show();
        }
    }

    private static class ViewHolder {
        View cardRoot;
        TextView tvIssuer;
        TextView tvLabel;
        TextView tvCode;
        TextView tvTapToCopy;
        TextView tvRemaining;
        Button btnDetail;
        View layoutMainCode;
        Button btnToggleTriplet;
        View layoutTriplet;
        View viewTripletDivider;
        TextView tvTripletHint;
        TextView tvSlotPrevLabel;
        TextView tvSlotCurrLabel;
        TextView tvSlotNextLabel;
        TextView tvSlotPrev;
        TextView tvSlotCurr;
        TextView tvSlotNext;
        View slotPrev;
        View slotCurr;
        View slotNext;
    }
}
