package com.isa.jump.plugin;

import com.vividsolutions.jump.workbench.model.Layer;
import com.vividsolutions.jump.workbench.model.Layerable;
import com.vividsolutions.jump.workbench.plugin.PlugInContext;
import com.vividsolutions.jump.workbench.ui.LayerViewPanel;
import com.vividsolutions.jump.workbench.ui.TaskFrame;
import com.vividsolutions.jump.workbench.ui.plugin.scalebar.ScaleBarRenderer;
import com.vividsolutions.jump.workbench.ui.renderer.LayerRenderer;
import com.vividsolutions.jump.workbench.ui.renderer.Renderer;
import com.vividsolutions.jump.workbench.ui.renderer.RenderingManager;
import com.vividsolutions.jump.workbench.ui.renderer.style.BasicStyle;
import com.vividsolutions.jump.workbench.ui.renderer.style.ColorThemingStyle;

import javax.print.attribute.Attribute;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.print.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import com.vividsolutions.jump.workbench.ui.renderer.style.Style;
import org.openjump.core.ui.plugin.view.NorthArrowRenderer;
import org.openjump.core.ui.plugin.view.helpclassescale.ShowScaleRenderer;

/**
 * This class performs basic vector or raster printing of the LayerViewPanel
 * set in the constructor.
 * Set the printLayerables collection to invoke the custom render() method or
 * override it with your own.
 * The static optimizeForVectors() method is provided for your convenience.
 *
 * @author Larry Becker
 */
public class PrinterDriver implements Printable {

  protected static final double dotsPerInch = 72d;
  protected static final String PRINTING_PASS = "Printing (pass" + " ";
  protected int printCallCount = 0;
  protected double printTotalHeight = 0;
  protected LayerViewPanel panel;
  protected TaskFrame taskFrame = null;
  //final Throwable[] throwable = new Throwable[] { null };
  protected boolean printBorder = false;
  protected PlugInContext context;
  protected int resolutionFactor = 1;
  protected ArrayList<Layerable> printLayerables = null;  //use RenderingManager when null
  protected boolean singlePageMode = true;

  public ArrayList<Layerable> getPrintLayerables() {
    return printLayerables;
  }

  public void setPrintLayerables(ArrayList<Layerable> printLayerables) {
    this.printLayerables = printLayerables;
  }

  public boolean getSinglePageMode() {
    return singlePageMode;
  }

  public void setSinglePageMode(boolean singlePageMode) {
    this.singlePageMode = singlePageMode;
  }

  public boolean getPrintBorder() {
    return printBorder;
  }

  public void setPrintBorder(boolean printBorder) {
    this.printBorder = printBorder;
  }

  public int getResolutionFactor() {
    return resolutionFactor;
  }

  public void setResolutionFactor(int resolutionFactor) {
    this.resolutionFactor = resolutionFactor;
  }

  public PrinterDriver(PlugInContext context, LayerViewPanel panel) {
    this.context = context;
    this.panel = panel;
  }

  public void setTaskFrame(TaskFrame taskFrame) {
    this.taskFrame = taskFrame;
  }

  /**
   * An alternate method of rendering that tries to avoid anything that would
   * cause PeekGraphics to set the raster mode of printing.  It is your
   * repsonsibility to turn off transparency before calling.
   *
   * @param graphics       graphic context
   * @param layersReversed - pass the list of layers to render in reverse order
   */
  protected void render(Graphics2D graphics, Collection<Layerable> layersReversed) {
    for (Layerable layerable : layersReversed) {
      if (!layerable.isVisible()) continue;
      if (layerable instanceof Layer) {
        // need to avoid using image buffers of any kind
        Layer layer = (Layer) layerable;
        LayerRenderer renderer = new LayerRenderer(layer, panel);
        renderer.createRunnable(); //don't run the runnable
        renderer.getSimpleFeatureCollectionRenderer().copyTo(graphics);
        renderer.clearImageCache();  //free memory
      } else {  // a WMSLayer or something like that
        Renderer renderer = panel.getRenderingManager()
            .createRenderer(layerable);
        Runnable runnable = renderer.createRunnable();
        if (runnable == null) continue;  //skip inactive layers
        runnable.run();
        renderer.copyTo(graphics);
        renderer.clearImageCache();  //free memory
      }
    }
    if (ScaleBarRenderer.isEnabled(panel)) {
      Renderer renderer = new ScaleBarRenderer(panel, taskFrame);
      renderer.copyTo(graphics);
      renderer.clearImageCache();  //free memory
    }
    if (NorthArrowRenderer.isEnabled(panel)) {
      Renderer renderer = new NorthArrowRenderer(panel);
      renderer.copyTo(graphics);
      renderer.clearImageCache();  //free memory
    }
    if (ShowScaleRenderer.isEnabled(panel)) {
      Renderer renderer = new ShowScaleRenderer(panel);
      renderer.copyTo(graphics);
      renderer.clearImageCache();  //free memory
    }
  }


  public int print(Graphics g, PageFormat pf, int page)
      throws PrinterException {

    if (singlePageMode && (page > 0)) { /* only one page, and 'page' is zero-based */
      return NO_SUCH_PAGE;
    }
    //Optimize the graphics context for printing
    Graphics2D graphics = (Graphics2D) g;
    //these hints will probably have little effect, but they don't hurt
    graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
        RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
        RenderingHints.VALUE_RENDER_QUALITY);
    graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
        RenderingHints.VALUE_STROKE_NORMALIZE);
//		graphics.setRenderingHint( RenderingHints.KEY_ANTIALIASING,
//				RenderingHints.VALUE_ANTIALIAS_ON);
    if (printBorder) {
      Rectangle2D pageBorder = new Rectangle2D.Double(
          pf.getImageableX(), pf.getImageableY(),
          pf.getImageableWidth(), pf.getImageableHeight());

      graphics.setPaint(Color.black);
      graphics.draw(pageBorder);
    }
    //this can take a while.  Let the user know something is happening
    printCallCount++;
    Rectangle r = graphics.getClipBounds();
    if (r != null) {
      int correctedPanelHeight = Math.round(panel.getHeight() / (float) resolutionFactor);
      if (r.height > correctedPanelHeight) {
        graphics.setClip(r.x, r.y, r.width, correctedPanelHeight);
      }
      int yBottom = r.y + r.height;
      if (printCallCount == 1) {
        printTotalHeight = yBottom;
        context.getWorkbenchFrame().setStatusMessage(
            PRINTING_PASS
                + printCallCount
                + ") Y = " + yBottom);
      } else {
        int percent = (int) (100 * yBottom / printTotalHeight);
        context.getWorkbenchFrame().setStatusMessage(
            PRINTING_PASS
                + printCallCount
                + ") % = " + percent);
      }
    }
    /*
     * User (0,0) is typically outside the imageable area, so we must
     * translate by the X and Y values in the PageFormat to avoid clipping
     */
    graphics.translate(pf.getImageableX(), pf.getImageableY());
    /*
     * Multiplying the size of the imageable area and then drawing
     * with the inverse scale factor increases the apparent
     * resolution while maintaining the scale of everything
     */
    if (resolutionFactor != 1d)
      graphics.scale(1d / resolutionFactor, 1d / resolutionFactor);
    try {
      if (printLayerables != null) {
        render(graphics, printLayerables);  //use specialized renderer
      } else {  //try the default method of rendering
//				final boolean [] locked = { true };
//				panel.getRenderingManager().setRenderingMode(new Runnable() {
//					public void run() {
//						synchronized (locked) {
//							locked[0] = false;
//							locked.notify();
//						}
//					}
//				},RenderingManager.SINGLE_THREAD_QUEUE);
        panel.getRenderingManager().setRenderingMode(
            RenderingManager.EXECUTE_ON_EVENT_THREAD);
        panel.getRenderingManager().renderAll();
        panel.getRenderingManager().copyTo(graphics);
//				while (locked[0]) locked.wait();
        panel.getRenderingManager().setRenderingMode(
            RenderingManager.INTERACTIVE);
      }
    } catch (Exception e) {
      String message = (e.getMessage() == null) ? e.toString() : e.getMessage();
      System.err.println(message);
      throw new PrinterException(message);
    }
    /* tell the caller that this page is part of the printed document */
    return PAGE_EXISTS;
  }

  /**
   * Modifies the styles of the passed layerable ArrayList to optimize them
   * for vector printing
   *
   * @param printLayerables     ArrayList
   * @param removeThemeFills    - remove fills from color themed layers
   * @param removeBasicFill     - remove fills from basic styles
   * @param changeLineWidth     - multiply line width by lineWidthMultiplier
   * @param lineWidthMultiplier - usually between 0 and 1.0
   * @param removeTransparency  - remove the transparency to keep from rasterizing
   * @return old Layer Style collections to restore later
   */
  public static ArrayList<Collection<Style>> optimizeForVectors(
      ArrayList<Layerable> printLayerables,
      boolean removeThemeFills, boolean removeBasicFill,
      boolean changeLineWidth, float lineWidthMultiplier, boolean removeTransparency) {
    if (!(removeThemeFills || removeBasicFill
        || changeLineWidth || removeTransparency))
      return null;
    ArrayList<Collection<Style>> oldStyleList = new ArrayList<>(printLayerables.size());
    for (Layerable layerable : printLayerables) {
      if (layerable instanceof Layer) {
        Layer layer = (Layer) layerable;
        final Collection<Style> oldStyles = layer.cloneStyles(); // copy
        oldStyleList.add(oldStyles); // save to restore later
        final Collection<Style> currentStyles = layer.getStyles();
        for (Style style : currentStyles) {
          if (style instanceof BasicStyle) {
            BasicStyle basicStyle = (BasicStyle) style;
            if (removeTransparency)
              basicStyle.setAlpha(255); // 255 is opaque
            if (removeBasicFill)
              basicStyle.setRenderingFill(false);
            if (changeLineWidth) {
              float lineWidth = basicStyle.getLineWidth();
              basicStyle.setFractionalLineWidth(
                  lineWidth * lineWidthMultiplier);
            }
          } else if (style instanceof ColorThemingStyle) {
            ColorThemingStyle themedStyle = (ColorThemingStyle) style;
            if (removeTransparency)
              themedStyle.setAlpha(255); // 255 is opaque
            Map<Object, BasicStyle> attributeValueToBasicStyleMap = themedStyle
                .getAttributeValueToBasicStyleMap();
            for (Map.Entry<Object, BasicStyle> entry : attributeValueToBasicStyleMap.entrySet()) {
              //Object attribute = entry.getKey();
              BasicStyle basicStyle = entry.getValue();
              if (removeThemeFills)
                basicStyle.setRenderingFill(false);
              if (changeLineWidth) {
                float lineWidth = basicStyle.getLineWidth();
                basicStyle.setFractionalLineWidth(
                    lineWidth * lineWidthMultiplier);
              }
            }
          }
        }
      }
    }
    return oldStyleList;
  }

  /**
   * Workaround JVM difficiencies in the confusing and conflicting printing API.
   *
   * @param attributeSet printing attributes
   * @param printerJob   object controlling printing
   * @return what PrinterJob.getPageFormat(attributeSet) should have returned:
   * the current PageFormat.
   */
  static public PageFormat getPageFormat(PrintRequestAttributeSet attributeSet, PrinterJob printerJob) {
    PageFormat pageFormat = printerJob.getPageFormat(attributeSet);
    Attribute[] atts = attributeSet.toArray();
    for (Attribute att : atts) {
      if (att instanceof MediaPrintableArea) {
        MediaPrintableArea printableArea = (MediaPrintableArea) att;
        Paper paper = pageFormat.getPaper();
        float[] area = printableArea
            .getPrintableArea(MediaPrintableArea.INCH);
        paper.setImageableArea((double) area[0] * dotsPerInch,
            (double) area[1] * dotsPerInch,
            (double) area[2] * dotsPerInch,
            (double) area[3] * dotsPerInch);
        pageFormat.setPaper(paper);
      } else if (att instanceof OrientationRequested) {
        OrientationRequested orientation = (OrientationRequested) att;
        if (orientation.equals(OrientationRequested.LANDSCAPE)) {
          pageFormat.setOrientation(PageFormat.LANDSCAPE);
        } else if (orientation.equals(OrientationRequested.PORTRAIT)) {
          pageFormat.setOrientation(PageFormat.PORTRAIT);
        } else if (orientation
            .equals(OrientationRequested.REVERSE_LANDSCAPE)) {
          pageFormat.setOrientation(PageFormat.REVERSE_LANDSCAPE);
        } else {
          pageFormat.setOrientation(PageFormat.LANDSCAPE);
        }
      } else if (att instanceof MediaSize || att instanceof MediaSizeName) {
        MediaSize mediaSize;
        if (att instanceof MediaSizeName) {
          mediaSize =
              MediaSize.getMediaSizeForName((MediaSizeName) att);
        } else {
          mediaSize = (MediaSize) att;
        }
        Paper paper = pageFormat.getPaper();
        paper.setSize(mediaSize.getX(MediaSize.INCH) * dotsPerInch, mediaSize
            .getY(MediaSize.INCH) * dotsPerInch);
        pageFormat.setPaper(paper);
      }
    }
    return pageFormat;
  }

  /**
   * Disables double buffering on passed component by
   * disabling it in the current RepaintManager
   *
   * @param c Component to use
   */
  public static void disableDoubleBuffering(Component c) {
    RepaintManager currentManager = RepaintManager.currentManager(c);
    currentManager.setDoubleBufferingEnabled(false);
  }

  /**
   * Reenables double buffering on passed component
   * using the current RepaintManager
   *
   * @param c Component to use
   */
  public static void enableDoubleBuffering(Component c) {
    RepaintManager currentManager = RepaintManager.currentManager(c);
    currentManager.setDoubleBufferingEnabled(true);
  }

}
