/*
 * The Unified Mapping Platform (JUMP) is an extensible, interactive GUI
 * for visualizing and manipulating spatial features with geometry and attributes.
 *
 * JUMP is Copyright (C) 2003 Vivid Solutions
 *
 * This program implements extensions to JUMP and is
 * Copyright (C) 2004 Integrated Systems Analysts, Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * For more information, contact:
 *
 * Integrated Systems Analysts, Inc.
 * 630C Anchors St., Suite 101
 * Fort Walton Beach, Florida
 * USA
 *
 * (850)862-7321
 * www.ashs.isa.com
 */


package com.isa.jump.plugin;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.DefaultFontMapper;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.vividsolutions.jump.I18N;
import com.vividsolutions.jump.geom.EnvelopeUtil;
import com.vividsolutions.jump.workbench.WorkbenchContext;
import com.vividsolutions.jump.workbench.datasource.SaveFileDataSourceQueryChooser;
import com.vividsolutions.jump.workbench.model.Layer;
import com.vividsolutions.jump.workbench.model.LayerManager;
import com.vividsolutions.jump.workbench.model.Layerable;
import com.vividsolutions.jump.workbench.plugin.AbstractPlugIn;
import com.vividsolutions.jump.workbench.plugin.EnableCheckFactory;
import com.vividsolutions.jump.workbench.plugin.MultiEnableCheck;
import com.vividsolutions.jump.workbench.plugin.PlugInContext;
import com.vividsolutions.jump.workbench.ui.*;
import com.vividsolutions.jump.workbench.ui.plugin.PersistentBlackboardPlugIn;
import com.vividsolutions.jump.workbench.ui.plugin.scalebar.ScaleBarRenderer;

import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;

import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;

import com.vividsolutions.jump.workbench.ui.renderer.style.Style;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.openjump.core.ui.plugin.view.NorthArrowRenderer;
import org.openjump.core.ui.plugin.view.helpclassescale.ShowScaleRenderer;
import org.openjump.core.ui.util.ScreenScale;

/**
 * This class installs a File->Print menu option and
 * performs basic vector or raster printing of the current
 * Layer View Panel or the area within the fence, if present.
 * The vertical extent may be modified to fit the paper size.
 *
 * @author Larry Becker
 */
public class PrintPlugIn extends AbstractPlugIn {

  /**
   * Name of this Plugin.
   */
  public static final String PRINT_PLUGIN_NAME = "SkyPrinterPlugin";

  /**
   * Version of this Plugin.
   */
  public static final String PRINT_PLUGIN_VERSION = "2.0";

  public static final double INCH = 72;
  public static final double MILLIMETER = 72.0 / 25.4;
  public double UNIT = MILLIMETER;
  //public final int HALF_INCH = 36;

  I18N i18n = I18N.getInstance("com.isa.jump.plugin");
  private final String PRINT_MENU = i18n.get("PrintPlugIn.Print");
  //private final String PRINT_AS_GROUP = "Force";
  //private final String PRINT_AS_RASTER = "Force printing as a raster";
  //private final String PRINT_AS_VECTORS = "Force printing as vectors (no transparency)";
  private final String REMOVE_TRANSPARENCY = i18n.get("PrintPlugIn.Remove-Transparency");
  private final String REMOVE_BASIC_FILLS = i18n.get("PrintPlugIn.Remove-basic-fills");
  private final String REMOVE_THEME_FILLS = i18n.get("PrintPlugIn.Remove-theme-fills");
  private final String CHANGE_LINE_WIDTH = i18n.get("PrintPlugIn.Change-line-width");
  private final String LINE_WIDTH_PERCENT = i18n.get("PrintPlugIn.Line-width-percent");
  private final String LINE_WIDTH_TOOLTIP = i18n.get("PrintPlugIn.Line-width-tooltip"); // 0-300
  private final String PDF_PAGE_TOOLTIP = i18n.get("PrintPlugIn.PDF-page-tooltip");   // MM
  private final String PRINT_BORDER = i18n.get("PrintPlugIn.Print-border");
  private final String PRINT_OPTIONS = i18n.get("PrintPlugIn.Print-options");
  private final String PRINTER_NOT_FOUND = i18n.get("PrintPlugIn.Printer-not-found");
  private final String RESOLUTION_MULTIPLIER = i18n.get("PrintPlugIn.Resolution-multiplier");
  private final String EXPAND_TO_FIT = i18n.get("PrintPlugIn.Expand-to-fit");
  private final String PRINT_AREA_IN_FENCE = i18n.get("PrintPlugIn.Print-area-in-fence");
  private final String PRINT_AREA_IN_BOUNDS = i18n.get("PrintPlugIn.Print-area-in-selection-bounds");
  //	private final String RESOLUTION_MULTIPLIER_TOOLTIP = "1 - 4";
  //  private final String OUT_OF_RANGE          = i18n.get("print","out of range");
  private final String FINISHED_MESSAGE = i18n.get("PrintPlugIn.Finished-message");
  private final String PRINT_TO_PDF = i18n.get("PrintPlugIn.Print-to-PDF");
  private final String PDF_META_TITLE = i18n.get("PrintPlugIn.PDF-title");
  private final String PDF_META_SUBJECT = i18n.get("PrintPlugIn.PDF-subject");
  private final String PDF_META_AUTHOR = i18n.get("PrintPlugIn.PDF-author");
  private final String PDF_META_KEYWORDS = i18n.get("PrintPlugIn.PDF-keywords");
  private final String PDF_META_TITLE_TOOLTIP = i18n.get("PrintPlugIn.PDF-title-tooltip");
  private final String PDF_META_SUBJECT_TOOLTIP = i18n.get("PrintPlugIn.PDF-subject-tooltip");
  private final String PDF_META_AUTHOR_TOOLTIP = i18n.get("PrintPlugIn.PDF-author-tooltip");
  private final String PDF_META_KEYWORDS_TOOLTIP = i18n.get("PrintPlugIn.PDF-keywords-tooltip");
  private final String SAVE_PDF = i18n.get("PrintPlugIn.Save-PDF");
  private final String PDF_FILES = i18n.get("PrintPlugIn.PDF-files");
  private final String PDF_PAGE_WIDTH = i18n.get("PrintPlugIn.PDF-page-width");
  private final String PDF_PAGE_HEIGHT = i18n.get("PrintPlugIn.PDF-page-height");
  private final String PAPER_SIZE = i18n.get("PrintPlugIn.Paper-size");
  private final String PAPER_SIZE_TOOLTIP = i18n.get("PrintPlugIn.Please-select-the-papersize");
  private final String PAPER_SIZE_CUSTOM = i18n.get("PrintPlugIn.custom-papersize");
  private final String PORTRAIT = i18n.get("PrintPlugIn.Portait");
  private final String LANDSCAPE = i18n.get("PrintPlugIn.Landscape");
  private final String PORTRAIT_TOOLTIP = i18n.get("PrintPlugIn.Portrait-orientation");
  private final String LANDSCAPE_TOOLTIP = i18n.get("PrintPlugIn.Landscape-orientation");
  private final String ORIENTATION_BUTTON_GROUP = "orientationButtonGroup";
  private final String DATE_FORMAT_STRING = i18n.get("PrintPlugIn.dateFormatString");
  private final String PDF_SUBJECT = i18n.get("PrintPlugIn.PDF-Subject");
  private final String SCALE_PRINT = i18n.get("PrintPlugIn.scale");
  private final String SCALE_PRINT_TOOLTIP = i18n.get("PrintPlugIn.scale-true-printing");
  private final String SCALE_MAXIMUM_VIEW = i18n.get("PrintPlugIn.scale-maximum-view");
  private final String SCALE_FULL_EXTENT = i18n.get("PrintPlugIn.scale-full-extent");

  private boolean printToPDF = false;
  private PlugInContext pluginContext;
  private PrintService printService = null;

  private LayerViewPanel printPanel = null;
  private int resolutionFactor = 1; //increase resolution by this factor
  //private boolean forceRaster = false;
  //private boolean forceVector = false;
  private boolean removeTransparency = false;
  private boolean removeThemeFills = false;
  private boolean removeBasicFills = false;
  private boolean changeLineWidth = true;
  private double lineWidthPercent = 25.0f;
  private float lineWidthMultiplier = 0.0f;
  private boolean printBorder = false;
  private boolean expandToFit = true;
  private boolean printFenceArea = false;
  private boolean printBoundsArea = true;
  private boolean doubleImageResolution = false;
  //private Envelope printEnvelope;
  PrintRequestAttributeSet attributeSet = new HashPrintRequestAttributeSet();
  private ArrayList<Layerable> printLayerables;
  private Envelope windowEnvelope = null;
  private Geometry fence = null;
  private JCheckBox printAreaInBoundsCheckBox;
  private JCheckBox printBorderCheckBox;
  private JTextField pdfTitleTextField;
  private JTextField pdfSubjectTextField;
  private JTextField pdfAuthorTextField;
  private JTextField pdfKeywordsTextField;
  private double pdfPageWidth = 210;
  private double pdfPageHeight = 297;
  private JTextField pageWidthTextField;
  private JTextField pageHeightTextField;
  private boolean landscapeOrientation = false;
  private Object mediaSizeNameObject = MediaSizeName.ISO_A4;
  private String scaleValue;


  //TODO: plugin to set page format (PrintRequestAttributeSet)
  //	printerJob.pageDialog(attributeSet);
  public PrintRequestAttributeSet getPrintRequestAttributeSet() {
    return attributeSet;
  }

  public void setPrintRequestAttributeSet(PrintRequestAttributeSet attributeSet) {
    this.attributeSet = attributeSet;
  }
//	/**
//	 * @param forceRaster new value of forceRaster
//	 * @return old value of forceRaster
//	 */
//	public boolean setForceRaster(boolean forceRaster){
//		boolean old = this.forceRaster;
//		this.forceRaster = forceRaster;
//		if (forceRaster)
//			forceVector = false;
//		return old;
//	}
//	/**
//	 * @param forceVector new value of forceVector
//	 * @return old value of forceVector
//	 */
//	public boolean setForceVector(boolean forceVector){
//		boolean old = this.forceVector;
//		this.forceVector = forceVector;
//		if (forceVector)
//			forceRaster = false;
//		return old;
//	}

  public void initialize(PlugInContext context) {
    context.getFeatureInstaller().addMainMenuPlugin(this,
        new String[]{MenuNames.FILE}, PRINT_MENU, false, null,
        PrintPlugIn.createEnableCheck(context.getWorkbenchContext()));
  }

  public boolean execute(final PlugInContext context) {
    reportNothingToUndoYet(context);
    MultiInputDialog dialog = new MultiInputDialog(
        context.getWorkbenchFrame(), PRINT_OPTIONS, true);
    fence = context.getLayerViewPanel().getFence();
    printFenceArea = (fence != null);
    dialog.addCheckBox(REMOVE_TRANSPARENCY, removeTransparency);
    dialog.addCheckBox(EXPAND_TO_FIT, expandToFit);
    dialog.addCheckBox(PRINT_AREA_IN_FENCE, printFenceArea);
    dialog.getCheckBox(PRINT_AREA_IN_FENCE).setEnabled(
        context.getLayerViewPanel().getFence() != null);

    // printBoundsArea makes only sense if we do not printFenceArea, because we can only do one thing at same time
    printBoundsArea = printFenceArea ? false : context.getLayerViewPanel()
        .getSelectionManager().getSelectedItems().size() == 1;
    printAreaInBoundsCheckBox = dialog.addCheckBox(PRINT_AREA_IN_BOUNDS, printBoundsArea);
    printAreaInBoundsCheckBox.setEnabled(printBoundsArea);
    //ActionListener to control the printAreaInBoundsCheckBox
    dialog.getCheckBox(PRINT_AREA_IN_FENCE).addActionListener(e -> {
      // printBoundsArea makes only sense if we do not printFenceArea, because we can only do one thing at same time
      printBoundsArea = !((JCheckBox) e.getSource()).isSelected() && context.getLayerViewPanel().getSelectionManager().getSelectedItems().size() == 1;
      printAreaInBoundsCheckBox.setEnabled(printBoundsArea);
      // deselect if printBoundsArea is false
      if (!printBoundsArea) printAreaInBoundsCheckBox.setSelected(false);
    });

    dialog.addCheckBox(PRINT_BORDER, printBorder);
    printBorderCheckBox = dialog.getCheckBox(PRINT_BORDER);
    dialog.addCheckBox(REMOVE_BASIC_FILLS, removeBasicFills);
    dialog.addCheckBox(REMOVE_THEME_FILLS, removeThemeFills);
    dialog.addCheckBox(CHANGE_LINE_WIDTH, changeLineWidth);
    dialog.addDoubleField(LINE_WIDTH_PERCENT, lineWidthPercent, 4, LINE_WIDTH_TOOLTIP);
//        dialog.addIntegerField(RESOLUTION_MULTIPLIER,
//        		resolutionFactor,4,RESOLUTION_MULTIPLIER_TOOLTIP);
    dialog.addCheckBox(RESOLUTION_MULTIPLIER, doubleImageResolution);

//        dialog.addRadioButton(PRINT_AS_RASTER, PRINT_AS_GROUP, forceRaster,"");
//        dialog.addRadioButton(PRINT_AS_VECTORS, PRINT_AS_GROUP, forceVector,"");
    dialog.addCheckBox(PRINT_TO_PDF, printToPDF);
    // add ActionListener to enable/disable the print border checkbox and metadata fields
    dialog.getCheckBox(PRINT_TO_PDF).addActionListener(e -> {
      JCheckBox printToPdfCheckBox = (JCheckBox) e.getSource();
      if (printToPdfCheckBox.isSelected()) {
        // disable border print, because in PDF print mode there is no border, only on real paper
        printBorderCheckBox.setEnabled(false);
        // enable the PDF metadata fields
        pdfTitleTextField.setEnabled(true);
        pdfSubjectTextField.setEnabled(true);
        pdfAuthorTextField.setEnabled(true);
        pdfKeywordsTextField.setEnabled(true);
      } else {
        // enable border print in "normal" print mode
        printBorderCheckBox.setEnabled(true);
        // disable the PDF metadata fields
        pdfTitleTextField.setEnabled(false);
        pdfSubjectTextField.setEnabled(false);
        pdfAuthorTextField.setEnabled(false);
        pdfKeywordsTextField.setEnabled(false);
      }
    });
    // PDF metadata
    pdfTitleTextField = dialog.addTextField("     " + PDF_META_TITLE, context.getTask().getName(), 20, null, PDF_META_TITLE_TOOLTIP);
    pdfSubjectTextField = dialog.addTextField("     " + PDF_META_SUBJECT, PDF_SUBJECT, 20, null, PDF_META_SUBJECT_TOOLTIP);
    pdfAuthorTextField = dialog.addTextField("     " + PDF_META_AUTHOR, System.getProperty("user.name"), 20, null, PDF_META_AUTHOR_TOOLTIP);
    pdfKeywordsTextField = dialog.addTextField("     " + PDF_META_KEYWORDS, "OpenJUMP", 20, null, PDF_META_KEYWORDS_TOOLTIP);
    // disable the PDF metadata fields
    pdfTitleTextField.setEnabled(printToPDF);
    pdfSubjectTextField.setEnabled(printToPDF);
    pdfAuthorTextField.setEnabled(printToPDF);
    pdfKeywordsTextField.setEnabled(printToPDF);
    // ComboBox for papersize selection
    dialog.addComboBox(PAPER_SIZE, MediaSizeName.ISO_A4,
        Arrays.asList(MediaSizeName.ISO_A0, MediaSizeName.ISO_A1, MediaSizeName.ISO_A2, MediaSizeName.ISO_A3, MediaSizeName.ISO_A4, MediaSizeName.ISO_A5,
            MediaSizeName.NA_LETTER, PAPER_SIZE_CUSTOM), PAPER_SIZE_TOOLTIP);

    // add a Listener for managing the width and height fields
    dialog.getComboBox(PAPER_SIZE).addActionListener(e -> {
      mediaSizeNameObject = ((JComboBox) e.getSource()).getSelectedItem();
      if (mediaSizeNameObject instanceof MediaSizeName) {
        // if we have a real MediaSizeName, we disable the textfields and set the values
        MediaSize mediaSize = MediaSize.getMediaSizeForName((MediaSizeName) mediaSizeNameObject);
        pageWidthTextField.setEnabled(false);
        pageHeightTextField.setEnabled(false);
        // set the corresponding dimensions values for the selected papersize
        if (landscapeOrientation) {
          pageWidthTextField.setText(String.valueOf(mediaSize.getY(MediaSize.MM)));
          pageHeightTextField.setText(String.valueOf(mediaSize.getX(MediaSize.MM)));
        } else {
          pageWidthTextField.setText(String.valueOf(mediaSize.getX(MediaSize.MM)));
          pageHeightTextField.setText(String.valueOf(mediaSize.getY(MediaSize.MM)));
        }
      } else {
        // in all other cases we enable the textfields
        pageWidthTextField.setEnabled(true);
        pageHeightTextField.setEnabled(true);
      }
    });
    pageWidthTextField = dialog.addDoubleField(PDF_PAGE_WIDTH, pdfPageWidth, 4, PDF_PAGE_TOOLTIP);
    pageHeightTextField = dialog.addDoubleField(PDF_PAGE_HEIGHT, pdfPageHeight, 4, PDF_PAGE_TOOLTIP);
    pageWidthTextField.setEnabled(false);
    pageHeightTextField.setEnabled(false);

    // RadioButton for orientation choise
    dialog.addRadioButton(PORTRAIT, ORIENTATION_BUTTON_GROUP, true, PORTRAIT_TOOLTIP);
    dialog.addRadioButton(LANDSCAPE, ORIENTATION_BUTTON_GROUP, false, LANDSCAPE_TOOLTIP);
    // add a Listener for managing the width and height
    dialog.getRadioButton(LANDSCAPE).addChangeListener(e -> {
      if (((JRadioButton) e.getSource()).isSelected()) {
        if (!landscapeOrientation) { // we need a separate variable, because the ChangeEvent is fired multiple times
          // in Landscape Mode we exchange width and height
          landscapeOrientation = true;
          String oldWidth = pageWidthTextField.getText();
          pageWidthTextField.setText(pageHeightTextField.getText());
          pageHeightTextField.setText(oldWidth);
        }
      } else {
        if (landscapeOrientation) { // we need a separate variable, because the ChangeEvent is fired multiple times
          // and back to portrait again
          landscapeOrientation = false;
          String oldWidth = pageWidthTextField.getText();
          pageWidthTextField.setText(pageHeightTextField.getText());
          pageHeightTextField.setText(oldWidth);
        }
      }
    });

    // scale true printing
    // first determine the actual map scale
    int actualScale = (int) Math.floor(ScreenScale.getHorizontalMapScale(context.getLayerViewPanel().getViewport()));
    // build an array with possible, predefined scale factors, including the actual scale
    int[] scaleFactors = {actualScale, 100, 1000, 2500, 5000, 10000};
    // sort the array, because the actual scale should in the correct order viewed
    Arrays.sort(scaleFactors);
    // for the combobox we need a String array with the scale factors and have to insert "1:" on start
    String[] stringScaleFactors = new String[scaleFactors.length + 2];
    for (int i = 0; i < scaleFactors.length; i++) {
      stringScaleFactors[i] = "1:" + scaleFactors[i];
    }
    stringScaleFactors[scaleFactors.length] = SCALE_MAXIMUM_VIEW;
    stringScaleFactors[scaleFactors.length + 1] = SCALE_FULL_EXTENT;
    // add the combobox with the scale factors and the actual scale is selcted
    dialog.addComboBox(SCALE_PRINT, "1:" + actualScale, Arrays.asList(stringScaleFactors), SCALE_PRINT_TOOLTIP);
    dialog.getComboBox(SCALE_PRINT).setEditable(true);

    dialog.setVisible(true);
    if (dialog.wasOKPressed()) {
      removeTransparency = dialog.getBoolean(REMOVE_TRANSPARENCY);
//           	setForceVector(removeTransparency);
      expandToFit = dialog.getBoolean(EXPAND_TO_FIT);
      printFenceArea = dialog.getBoolean(PRINT_AREA_IN_FENCE);
      printBoundsArea = dialog.getBoolean(PRINT_AREA_IN_BOUNDS);
      //if neither of the following overrides are checked,
      //  the transparency setting will control
//        	if (dialog.getBoolean(PRINT_AS_RASTER))
//        		setForceRaster(true);        	
//           	if (dialog.getBoolean(PRINT_AS_VECTORS))
//           		setForceVector(true);       	    
      printToPDF = dialog.getBoolean(PRINT_TO_PDF);
      printBorder = dialog.getBoolean(PRINT_BORDER) && !printToPDF; // disable, because in PDF print mode there is no border, only on real paper
      removeBasicFills = dialog.getBoolean(REMOVE_BASIC_FILLS);
      removeThemeFills = dialog.getBoolean(REMOVE_THEME_FILLS);
      changeLineWidth = dialog.getBoolean(CHANGE_LINE_WIDTH);
      lineWidthPercent = dialog.getDouble(LINE_WIDTH_PERCENT);
      lineWidthMultiplier = (float) lineWidthPercent / 100f;
      doubleImageResolution = dialog.getBoolean(RESOLUTION_MULTIPLIER);
      if (doubleImageResolution)
        resolutionFactor = 2;
      else
        resolutionFactor = 1;
//         	resolutionFactor = dialog.getInteger(RESOLUTION_MULTIPLIER);
//         	resolutionFactor = dialog.getInteger(RESOLUTION_MULTIPLIER);
      pdfPageWidth = dialog.getDouble(PDF_PAGE_WIDTH);
      pdfPageHeight = dialog.getDouble(PDF_PAGE_HEIGHT);
      mediaSizeNameObject = dialog.getComboBox(PAPER_SIZE).getSelectedItem();
      scaleValue = (String) dialog.getComboBox(SCALE_PRINT).getSelectedItem();
      //background the whole printing operation
      new Thread(() -> {
        try {
          // we must set a resolution of 72 dpi to get the correct scale for the ShowScaleRenderer
          int oldScreenResolution = ScreenScale.getResolution();
          ScreenScale.setResolution(72);
          if (printToPDF)
            pdfCurrentWindow(context);
          else
            printCurrentWindow(context);
          // restore the old value
          ScreenScale.setResolution(oldScreenResolution);
        } catch (PrinterException e) {
          context.getErrorHandler().handleThrowable(e);
        }
        context.getLayerViewPanel().repaint();
      }).start();
    }
    return true;
  }

  protected void pdfCurrentWindow(PlugInContext context) {
    JFileChooser fileChooser = GUIUtil.createJFileChooserWithOverwritePrompting();
    fileChooser.setDialogTitle(SAVE_PDF);
    fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
    fileChooser.setMultiSelectionEnabled(false);
    GUIUtil.removeChoosableFileFilters(fileChooser);
    FileFilter fileFilter1 = GUIUtil.createFileFilter(PDF_FILES, new String[]{"pdf"});
    fileChooser.addChoosableFileFilter(fileFilter1);
    fileChooser.setFileFilter(fileFilter1);
    Calendar cal = Calendar.getInstance();
    Date date = cal.getTime();
    SimpleDateFormat df = new SimpleDateFormat(DATE_FORMAT_STRING);
    String dateStr = df.format(date);
    String suggestedFileName = context.getTask().getName() + "_" + dateStr + ".pdf";
    fileChooser.setSelectedFile(new File(suggestedFileName));
    // restore the last output directory
    String FILE_CHOOSER_DIRECTORY_KEY = SaveFileDataSourceQueryChooser.class.getName() + " - FILE CHOOSER DIRECTORY";
    String lastOutputDirectory = (String) PersistentBlackboardPlugIn.get(context.getWorkbenchContext()).get(FILE_CHOOSER_DIRECTORY_KEY);
    if (lastOutputDirectory != null) { // if the plugin runs the first time, there is nothing in the Blackboard
      fileChooser.setCurrentDirectory(new File(lastOutputDirectory));
    }
    if (JFileChooser.APPROVE_OPTION != fileChooser.showSaveDialog(context.getLayerViewPanel()))
      return;
    PersistentBlackboardPlugIn.get(context.getWorkbenchContext()).put(FILE_CHOOSER_DIRECTORY_KEY, fileChooser.getCurrentDirectory().getAbsolutePath());
    String pdfFileName = fileChooser.getSelectedFile().getPath();
    if (!(pdfFileName.toLowerCase().endsWith(".pdf")))
      pdfFileName = pdfFileName + ".pdf";

    pluginContext = context;
    printLayerables = new ArrayList<>(context.getLayerManager()
        .getLayerables(Layerable.class));  //includes Layers and WMSLayers
    Collections.reverse(printLayerables);  //print bottom to top
    //optimize layer styles for print type
    ArrayList<Collection<Style>> oldStyleList = PrinterDriver.optimizeForVectors(printLayerables,
        removeThemeFills, removeBasicFills,
        changeLineWidth, lineWidthMultiplier, removeTransparency);
    //create a printing panel with the current panel's LayerManager
    final Throwable[] throwable = new Throwable[]{null};
    printPanel = createLayerPanel(context.getLayerManager(), throwable);
    PDFDriver.disableDoubleBuffering(printPanel);
    PDFDriver pdfDriver = new PDFDriver(context, printPanel);

    ScaleBarRenderer.setEnabled(
        ScaleBarRenderer.isEnabled(context.getLayerViewPanel())
        , printPanel);  //transfer scale bar settings to print panel
    NorthArrowRenderer.setEnabled(
        NorthArrowRenderer.isEnabled(context.getLayerViewPanel())
        , printPanel);  //transfer North Arrow settings to print panel
    ShowScaleRenderer.setEnabled(
        ShowScaleRenderer.isEnabled(context.getLayerViewPanel())
        , printPanel);
    pdfDriver.setTaskFrame((TaskFrame) context.getWorkbenchFrame().getActiveInternalFrame());

    pdfDriver.setPrintBorder(printBorder);
    pdfDriver.setPrintLayerables(printLayerables);
    windowEnvelope = pluginContext.getLayerViewPanel().getViewport()
        .getEnvelopeInModelCoordinates();
    fence = pluginContext.getLayerViewPanel().getFence();

    try {

      try {
        // step 1: creation of a document-object
        Document document = new Document(
            new Rectangle((float) (pdfPageWidth * UNIT),
                (float) (pdfPageHeight * UNIT)));

        // step 2: creation of the writer
        PdfWriter writer = PdfWriter.getInstance(document,
            new FileOutputStream(pdfFileName));
        writer.setCropBoxSize(
            new Rectangle(0, 0, ((float) (pdfPageWidth * UNIT)),
                ((float) (pdfPageHeight * UNIT))));
        writer.setPdfVersion(PdfWriter.VERSION_1_5);
        writer.setViewerPreferences(PdfWriter.PageModeUseOC);
        // step 3: we open the document
        document.open();

        // add some metadata
        document.addAuthor(pdfAuthorTextField.getText());
        document.addCreator("OpenJUMP " + PRINT_PLUGIN_NAME + " " + PRINT_PLUGIN_VERSION);
        document.addKeywords(pdfKeywordsTextField.getText());
        document.addSubject(pdfSubjectTextField.getText());
        document.addTitle(pdfTitleTextField.getText());

        // step 4: we grab the ContentByte and do some stuff with it

        // we create a fontMapper and read all the fonts in the font directory
        DefaultFontMapper mapper = new DefaultFontMapper();
        FontFactory.registerDirectories();
//    			mapper.insertDirectory("c:\\windows\\fonts");

        PageFormat pageFormat = new PageFormat();
        Paper paper = new Paper();
        double width = pdfPageWidth * UNIT;
        double height = pdfPageHeight * UNIT;
        paper.setSize(width, height);
        paper.setImageableArea(0, 0, width, height);
        pageFormat.setPaper(paper);
        double w = pageFormat.getImageableWidth();
        double h = pageFormat.getImageableHeight();

        PdfContentByte cb = writer.getDirectContent();
        PdfTemplate tp = cb.createTemplate((float) w, (float) h);
        Graphics2D g2 = tp.createGraphics((float) w, (float) h, mapper);
        tp.setWidth((float) w);
        tp.setHeight((float) h);

        pdfDriver.setCb(tp);
        pdfDriver.setWriter(writer);

        try {
          initLayerViewPanel(pageFormat);
          pdfDriver.setResolutionFactor(resolutionFactor);
        } catch (Exception e) {
          String message = (e.getMessage() == null) ? e.toString() : e.getMessage();
          System.err.println(message);
        }

        //The simple method of printing to PDF
//    			try  { 
//    				printPanel.getViewport().zoomToFullExtent();
//    			}
//    			catch (NoninvertibleTransformException e) {
//    				String message = (e.getMessage()==null) ? e.toString(): e.getMessage();
//    				System.err.println(message);       		
//    			}			
//    			printPanel.paintComponent(g2);

        try {
          pdfDriver.print(g2, pageFormat, 0);
        } catch (PrinterException e) {
          String message = (e.getMessage() == null) ? e.toString() : e.getMessage();
          System.err.println(message);
        }

        g2.dispose();
        tp.sanityCheck(); // all the g2 content is written to tp, not cb
        cb.addTemplate(tp, 0, 0);
        cb.sanityCheck();
        // step 5: we close the document
        document.close();
      } catch (DocumentException | IOException de) {
        System.err.println(de.getMessage());
      }
    } finally {

      if (oldStyleList != null) {  // restore the original styles
        boolean wasFiringEvents = printPanel.getLayerManager().isFiringEvents();
        printPanel.getLayerManager().setFiringEvents(false);
        int j = 0;
        for (Layerable layerable : printLayerables) {
          if (layerable instanceof Layer) {
            Layer layer = (Layer) layerable;
            layer.setStyles(oldStyleList.get(j++));
          }
        }
        printPanel.getLayerManager().setFiringEvents(wasFiringEvents);
      }
      if (printPanel != null) {
        PrinterDriver.enableDoubleBuffering(printPanel);
        printPanel.dispose();
        printPanel = null;
      }
      context.getWorkbenchFrame().setStatusMessage(FINISHED_MESSAGE);
    }
  }


  protected void printCurrentWindow(PlugInContext context)
      throws PrinterException {
    pluginContext = context;
    //create a printing panel with the current panel's LayerManager
    final Throwable[] throwable = new Throwable[]{null};
    printPanel = createLayerPanel(context.getLayerManager(), throwable);
    PrinterDriver.disableDoubleBuffering(printPanel);
    windowEnvelope = pluginContext.getLayerViewPanel().getViewport()
        .getEnvelopeInModelCoordinates();
    fence = pluginContext.getLayerViewPanel().getFence();

    printLayerables = new ArrayList<>(context.getLayerManager()
        .getLayerables(Layerable.class));  //includes Layers and WMSLayers
    Collections.reverse(printLayerables);  //print bottom to top
    //Create and set up a custom PrinterDriver for use by PrinterJob
    PrinterDriver printerDriver = new PrinterDriver(context, printPanel);

    ScaleBarRenderer.setEnabled(
        ScaleBarRenderer.isEnabled(context.getLayerViewPanel())
        , printPanel);  //transfer scale bar settings to print panel
    NorthArrowRenderer.setEnabled(
        NorthArrowRenderer.isEnabled(context.getLayerViewPanel())
        , printPanel);  //transfer North Arrow settings to print panel
    ShowScaleRenderer.setEnabled(
        ShowScaleRenderer.isEnabled(context.getLayerViewPanel())
        , printPanel);
    printerDriver.setTaskFrame((TaskFrame) context.getWorkbenchFrame().getActiveInternalFrame());

    printerDriver.setPrintBorder(printBorder);
//    	if (!printerDriver.setResolutionFactor(resolutionFactor)) {
//    		context.getWorkbenchFrame()
//    		.setStatusMessage(RESOLUTION_MULTIPLIER + " " + OUT_OF_RANGE);
//    	}

    //optimize layer styles for print type
    ArrayList<Collection<Style>> oldStyleList = PrinterDriver.optimizeForVectors(printLayerables,
        removeThemeFills, removeBasicFills,
        changeLineWidth, lineWidthMultiplier, removeTransparency);
    //comment out the following line to test default rendering
    printerDriver.setPrintLayerables(printLayerables);
    try {

      // clear all attributes, to avoid some lookup and pageformat setup problems
      attributeSet.clear();
      PrinterJob printerJob = PrinterJob.getPrinterJob();
      // set the orientation and MediaSize
      if (landscapeOrientation) {
        attributeSet.add(OrientationRequested.LANDSCAPE);
      } else {
        attributeSet.add(OrientationRequested.PORTRAIT);
      }
      if (mediaSizeNameObject instanceof MediaSizeName) {
        attributeSet.add((MediaSizeName) mediaSizeNameObject);
      }

      DocFlavor flavor = DocFlavor.SERVICE_FORMATTED.PRINTABLE;
      if (printService == null) {
        PrintService[] services = PrintServiceLookup
            .lookupPrintServices(flavor, attributeSet);
        if (services.length > 0) {
          //System.out.println("selected printer " + services[0].getName());
          printService = services[0];
        } else {
          context.getWorkbenchFrame().warnUser(PRINTER_NOT_FOUND);
          return;  //exit via finally
        }
      }
      // This is a workaround for the problem of sometimes still getting a
      // raster even after optimizeForVectors()
//    		try {
//    			if (forceRaster) {
//    				sun.print.RasterPrinterJob.forceRaster = true;
//    				sun.print.RasterPrinterJob.forcePDL = false;
//    			} else if (forceVector) {
//    				sun.print.RasterPrinterJob.forcePDL = true;
//    				sun.print.RasterPrinterJob.forceRaster = false;
//    			}
//    		} catch (Exception e) { 
//    			//just eat any errors that occur if this doesn't exist
//    			context.getWorkbenchFrame().setStatusMessage(e.toString());
//    		}

      //-------------------- the actual print job --------------------------
      // set the JobName here, because under some *nix OS (Linux, OS X) we
      // do not get the printservices if a JobName is set during PrintServiceLookup
      attributeSet.add(new JobName(context.getTask().getName(), null));
      printerJob.setPrintService(printService);
      // 2014.03.31 <ms> attributeSet from printDialog() removed, to get a system print dialog in Windows and not the Java one
      if (printerJob.printDialog()) { //OK pressed
        PageFormat pageFormat = PrinterDriver
            .getPageFormat(attributeSet, printerJob);
        printerJob.setPrintable(printerDriver, pageFormat);
        try {
          initLayerViewPanel(pageFormat);
          printerDriver.setResolutionFactor(resolutionFactor);
        } catch (Exception e) {
          String message = (e.getMessage() == null) ? e.toString() : e.getMessage();
          System.err.println(message);
          throw new PrinterException(message);
        }
        printerJob.print(attributeSet);
      }
      if (throwable[0] != null) {
        String message = (throwable[0].getMessage() == null)
            ? throwable[0].toString() : throwable[0].getMessage();
        System.err.println(message);
        context.getErrorHandler().handleThrowable(
            (throwable[0] instanceof Exception)
                ? throwable[0]
                : new Exception(message));
      }

      //-------------------- end actual print job --------------------------
      printService = printerJob.getPrintService();  //save for next time
    } finally {

      if (oldStyleList != null) {  // restore the original styles
        boolean wasFiringEvents = printPanel.getLayerManager().isFiringEvents();
        printPanel.getLayerManager().setFiringEvents(false);
        int j = 0;
        for (Layerable layerable : printLayerables) {
          if (layerable instanceof Layer) {
            Layer layer = (Layer) layerable;
            layer.setStyles(oldStyleList.get(j++));
          }
        }
        printPanel.getLayerManager().setFiringEvents(wasFiringEvents);
      }
      if (printPanel != null) {
        PrinterDriver.enableDoubleBuffering(printPanel);
        printPanel.dispose();
        printPanel = null;
      }
      printerDriver = null;
      context.getWorkbenchFrame().setStatusMessage(FINISHED_MESSAGE);
    }

  }

  public static MultiEnableCheck createEnableCheck(WorkbenchContext workbenchContext) {
    EnableCheckFactory checkFactory = EnableCheckFactory.getInstance(workbenchContext);
    return new MultiEnableCheck()
        .add(checkFactory.createTaskWindowMustBeActiveCheck())
        .add(checkFactory.createAtLeastNLayerablesMustExistCheck(1));
  }

  /**
   * Construct a printing LayerViewPanel using the PlugInContext's LayerManager
   *
   * @param layerManager the layerManager
   * @return new LayerViewPanel
   */
  protected LayerViewPanel createLayerPanel(LayerManager layerManager,
                                            final Throwable[] throwable) {
    return new LayerViewPanel(
        layerManager, new LayerViewPanelContext() {
      public void setStatusMessage(String message) {
      }

      public void warnUser(String warning) {
      }

      public void handleThrowable(Throwable t) {
        throwable[0] = t;
      }
    });
  }

  protected Envelope computePrintPageEnvelope(Envelope windowEnvelope, PageFormat pf) {
    // keep width of window envelope - adjust height for page format
    double pageRatio = pf.getImageableHeight() / pf.getImageableWidth();
    double minX = windowEnvelope.getMinX();
    double maxX = windowEnvelope.getMaxX();
    double minY = windowEnvelope.getMinY();
    double maxY = windowEnvelope.getMaxY();
    // center and expand up and down option
//  	double centerY = (windowEnvelope.getMaxY() - windowEnvelope.getMinY()) / 2d;
//  	double height2 = (windowEnvelope.getWidth() * pageRatio) / 2d;
//  	minY = centerY - height2; 
//  	maxY = centerY + height2;
    // top justify and expand down option
    if (expandToFit) {
      minY = windowEnvelope.getMaxY() - (windowEnvelope.getWidth() * pageRatio);
      maxY = windowEnvelope.getMaxY();
    }
    return new Envelope(minX, maxX, minY, maxY);
  }

  protected void initLayerViewPanel(PageFormat pageFormat) throws Exception {
  	Envelope printEnvelope;
    if ((printFenceArea) && (fence != null)) {
      printEnvelope = computePrintPageEnvelope(fence.getEnvelopeInternal(),
          pageFormat);
    } else if (printBoundsArea) {
      printEnvelope = computePrintPageEnvelope(((pluginContext.getLayerViewPanel().getSelectionManager()
              .getSelectedItems().iterator().next())).getEnvelopeInternal(),
          pageFormat);
    } else if (scaleValue.equals(SCALE_MAXIMUM_VIEW)) {
      // we print the on screen area/view in the maximum on the paper: printEnvelope = LayerViewpanels Envelope
      printEnvelope = computePrintPageEnvelope(pluginContext.getLayerViewPanel().getViewport().getEnvelopeInModelCoordinates(), pageFormat);
    } else if (scaleValue.equals(SCALE_FULL_EXTENT)) {
      // we print in the maxumum extent of all visible layers minus 0.03 (for a small border)
      printEnvelope = computePrintPageEnvelope(EnvelopeUtil.bufferByFraction(pluginContext.getLayerManager().getEnvelopeOfAllLayers(true), 0.03), pageFormat);
    } else {
      printEnvelope = computePrintPageEnvelope(windowEnvelope, pageFormat);
    }

    int extentInPixelsX = (int) (pageFormat.getImageableWidth() * resolutionFactor);
    int extentInPixelsY = (int) (pageFormat.getImageableHeight() * resolutionFactor);
    if (!expandToFit) {
      double ratio = (printEnvelope.getHeight() / printEnvelope.getWidth());
      extentInPixelsY = (int) Math.round((ratio * pageFormat.getImageableWidth()) * resolutionFactor);
    }
    printPanel.setSize(extentInPixelsX, extentInPixelsY);

    // the following is for scale true printing
    // we print only in the specified scale, if we are not in printFenceArea
    // or printBoundsArea mode, because this makes no sense.
    double scale = 0;
    if (!(printFenceArea && (fence != null)) && !printBoundsArea && !scaleValue.equals(SCALE_MAXIMUM_VIEW) && !scaleValue.equals(SCALE_FULL_EXTENT)) {
      // try to parse the scaleValue from the ComboBox
      try {
        // first remove "1:" and second all non numeric stuff
        scale = Double.parseDouble(scaleValue.replaceFirst("^.*:", "").replaceAll("[^0-9]*", ""));
      } catch (NumberFormatException nfe) {
        scale = 0;
      }
    }
    // if we have a valid scale value, we scale
    if (scale > 0) {
      // compute the actual center on the screen
      Envelope screenEnvelope = pluginContext.getLayerViewPanel().getViewport().getEnvelopeInModelCoordinates();
      double xCenter = screenEnvelope.getMinX() + screenEnvelope.getWidth() / 2;
      double yCenter = screenEnvelope.getMinY() + screenEnvelope.getHeight() / 2;

      // envelope width/height for the scale
      double scaledEnvelopeWidth = extentInPixelsX / 72d * 2.54d / 100d * scale;
      double scaledEnvelopeHeight = extentInPixelsY / 72d * 2.54d / 100d * scale;
      // the new scaled Envelope
      Envelope scaledEnvelope = new Envelope(xCenter - scaledEnvelopeWidth / 2d, xCenter + scaledEnvelopeWidth / 2d, yCenter - scaledEnvelopeHeight / 2d, yCenter + scaledEnvelopeHeight / 2d);
      printPanel.getViewport().zoom(scaledEnvelope);
    } else {
      printPanel.getViewport().zoom(printEnvelope);
    }
  }
}

