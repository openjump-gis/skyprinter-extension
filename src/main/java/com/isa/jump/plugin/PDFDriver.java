package com.isa.jump.plugin;

import com.lowagie.text.pdf.*;
import com.vividsolutions.jump.workbench.model.Layer;
import com.vividsolutions.jump.workbench.model.Layerable;
import com.vividsolutions.jump.workbench.plugin.PlugInContext;
import com.vividsolutions.jump.workbench.ui.LayerViewPanel;
import com.vividsolutions.jump.workbench.ui.plugin.scalebar.ScaleBarRenderer;
import com.vividsolutions.jump.workbench.ui.renderer.LayerRenderer;
import com.vividsolutions.jump.workbench.ui.renderer.Renderer;
import com.vividsolutions.jump.workbench.ui.renderer.RenderingManager;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.openjump.core.ui.plugin.view.NorthArrowRenderer;
import org.openjump.core.ui.plugin.view.helpclassescale.ShowScaleRenderer;

public class PDFDriver extends PrinterDriver {

  PdfContentByte pdfContentByte = null;
  PdfWriter writer = null;

  public PDFDriver(PlugInContext context, LayerViewPanel panel) {
    super(context, panel);
  }

  public PDFDriver(PlugInContext context, LayerViewPanel panel,
                   PdfContentByte pdfContentByte, PdfWriter writer) {
    super(context, panel);
    this.pdfContentByte = pdfContentByte;
    this.writer = writer;
  }

  protected void render(Graphics2D graphics, Collection<Layerable> layersReversed) {
    PdfArray pdfArray = new PdfArray();

    ArrayList<PdfIndirectReference> pdfLayerRefs = new ArrayList<>();

    for (Layerable layerable : layersReversed) {
      if (!layerable.isVisible()) continue;

      PdfLayer pdfLayer = new PdfLayer(layerable.getName(), writer);
      pdfContentByte.beginLayer(pdfLayer);

      if (layerable instanceof Layer) {
        // need to avoid using image buffers of any kind
        Layer layer = (Layer) layerable;
        LayerRenderer renderer = new LayerRenderer(layer, panel);
        Runnable runnable = renderer.createRunnable();
        if (layer.getBlackboard().get(RenderingManager.USE_MULTI_RENDERING_THREAD_QUEUE_KEY, false)) {
          runnable.run(); //don't run the runnable unless it is backed by a database
          renderer.copyTo(graphics);
        } else {
          renderer.getSimpleFeatureCollectionRenderer().copyTo(graphics);
        }
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
      pdfContentByte.endLayer();
      // Put refs in a temporary list in order to reverse the list
      // and get the same order in Pdf and in OpenJUMP LayerTrees
      pdfLayerRefs.add(pdfLayer.getRef());
      //pdfArray.add(pdfLayer.getRef());
    }

    // Reverse the order : order will be the same in OpenJUMP and Pdf LayerTrees
    Collections.reverse(pdfLayerRefs);
    for (PdfIndirectReference ref : pdfLayerRefs) {
      pdfArray.add(ref);
    }

    if (ScaleBarRenderer.isEnabled(panel)) {
      PdfLayer pdfLayer = new PdfLayer(ScaleBarRenderer.CONTENT_ID, writer);
      pdfContentByte.beginLayer(pdfLayer);
      Renderer renderer = new ScaleBarRenderer(panel, taskFrame);
      renderer.copyTo(graphics);
      renderer.clearImageCache();  //free memory
      pdfContentByte.endLayer();
      pdfArray.add(pdfLayer.getRef());
    }
    if (NorthArrowRenderer.isEnabled(panel)) {
      PdfLayer pdfLayer = new PdfLayer(NorthArrowRenderer.CONTENT_ID, writer);
      pdfContentByte.beginLayer(pdfLayer);
      Renderer renderer = new NorthArrowRenderer(panel);
      renderer.copyTo(graphics);
      renderer.clearImageCache();  //free memory
      pdfContentByte.endLayer();
      pdfArray.add(pdfLayer.getRef());
    }
    if (ShowScaleRenderer.isEnabled(panel)) {
      PdfLayer pdfLayer = new PdfLayer(ShowScaleRenderer.CONTENT_ID, writer);
      pdfContentByte.beginLayer(pdfLayer);
      Renderer renderer = new ShowScaleRenderer(panel);
      renderer.copyTo(graphics);
      renderer.clearImageCache();  //free memory
      pdfContentByte.endLayer();
      pdfArray.add(pdfLayer.getRef());
    }

    PdfDictionary pdfDictionary = new PdfDictionary();
    pdfDictionary.put(PdfName.ORDER, pdfArray);
    writer.getOCProperties().put(PdfName.D, pdfDictionary);
  }

  public int print(Graphics g, PageFormat pf, int page)
      throws PrinterException {

    if (singlePageMode && (page > 0)) { /* only one page, and 'page' is zero-based */
      return NO_SUCH_PAGE;
    }
//		Optimize the graphics context for printing
    Graphics2D graphics = (Graphics2D) g;
//		these hints will probably have little effect, but they don't hurt
    graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
        RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
        RenderingHints.VALUE_RENDER_QUALITY);
    graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
        RenderingHints.VALUE_STROKE_NORMALIZE);
//		graphics.setRenderingHint( RenderingHints.KEY_ANTIALIASING,
//		RenderingHints.VALUE_ANTIALIAS_ON);
    if (printBorder) {
      Rectangle2D pageBorder = new Rectangle2D.Double(
          pf.getImageableX(), pf.getImageableY(),
          pf.getImageableWidth(), pf.getImageableHeight());

      graphics.setPaint(Color.black);
      graphics.draw(pageBorder);
    }
//		this can take a while.  Let the user know something is happening
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
      render(graphics, printLayerables);  //use specialized renderer
    } catch (Exception e) {
      String message = (e.getMessage() == null) ? e.toString() : e.getMessage();
      System.err.println(message);
      throw new PrinterException(message);
    }
    /* tell the caller that this page is part of the printed document */
    return PAGE_EXISTS;
  }


  public PdfContentByte getCb() {
    return pdfContentByte;
  }

  public void setCb(PdfContentByte pdfContentByte) {
    this.pdfContentByte = pdfContentByte;
  }

  public PdfWriter getWriter() {
    return writer;
  }

  public void setWriter(PdfWriter writer) {
    this.writer = writer;
  }

}
