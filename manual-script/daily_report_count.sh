
    DISPLAY_DATE=$(date -d "$CURRENT_DATE" +"%d %B %Y")

    echo "$DISPLAY_DATE,$COUNT"

    TOTAL=$((TOTAL + COUNT))

done

echo ""
echo "Total Transaction,$TOTAL"

} > "$CSV_FILE"

# =========================
# CLEAN TEMP
# =========================
rm -f "$TMP_FILE"

echo "======================================"
echo "Report Generated Successfully"
echo "File : $CSV_FILE"
echo "======================================" 