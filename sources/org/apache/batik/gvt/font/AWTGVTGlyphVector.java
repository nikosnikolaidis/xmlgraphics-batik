/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.gvt.font;

import java.awt.font.FontRenderContext;
import java.awt.font.GlyphJustificationInfo;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.geom.GeneralPath;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Stroke;
import org.apache.batik.gvt.GraphicsNodeRenderContext;
import org.apache.batik.gvt.text.GVTAttributedCharacterIterator;
import org.apache.batik.gvt.text.ArabicTextHandler;
import java.text.AttributedCharacterIterator;
import java.text.CharacterIterator;
import java.awt.font.TextAttribute;

/**
 * This is a wrapper class for a java.awt.font.GlyphVector instance.
 *
 * @author <a href="mailto:bella.robinson@cmis.csiro.au">Bella Robinson</a>
 * @version $Id$
 */
public final class AWTGVTGlyphVector implements GVTGlyphVector {

    private final GlyphVector awtGlyphVector;
    private final AWTGVTFont gvtFont;
    private CharacterIterator ci;

    private Point2D[] glyphPositions;
    private Point2D[] defaultGlyphPositions;

    // need to keep track of the glyphTransforms since GlyphVector doesn't seem to
    private AffineTransform[] glyphTransforms;

    // these are for caching the glyph outlines
    private Shape[] glyphOutlines;
    private Shape[] glyphVisualBounds;
    private Shape[] glyphLogicalBounds;
    private boolean[] glyphVisible;
    private GeneralPath outline;
    private Rectangle2D logicalBounds;
    private float scaleFactor;

    /**
     * Creates and new AWTGVTGlyphVector from the specified GlyphVector
     * and AWTGVTFont objects.
     *
     * @param glyphVector The glyph vector that this one will be based upon.
     * @param font The font that is creating this glyph vector.
     * @param scaleFactor The scale factor to apply to the glyph vector.
     * IMPORTANT: This is only required because the GlyphVector class doesn't
     * handle font sizes less than 1 correctly. By using the scale factor we
     * can use a GlyphVector created by a larger font and then scale it down to
     * the correct size.
     * @param ci The character string that this glyph vector represents.
     */
    public AWTGVTGlyphVector(GlyphVector glyphVector, AWTGVTFont font,
                             float scaleFactor, CharacterIterator ci) {

        this.awtGlyphVector = glyphVector;
        this.gvtFont = font;
        this.scaleFactor = scaleFactor;
        this.ci = ci;

        int numGlyphs = glyphVector.getNumGlyphs();
        outline = null;
        logicalBounds = null;
        glyphTransforms = new AffineTransform[numGlyphs];
        glyphPositions = new Point2D.Float[numGlyphs];
        defaultGlyphPositions = new Point2D.Float[numGlyphs];
        glyphOutlines = new Shape[numGlyphs];
        glyphVisualBounds = new Shape[numGlyphs];
        glyphLogicalBounds = new Shape[numGlyphs];
        glyphVisible = new boolean[numGlyphs];
        for (int i = 0; i < numGlyphs; i++) {
            glyphVisible[i] = true;
        }
    }

    /**
     * Returns the GVTFont associated with this GVTGlyphVector.
     */
    public GVTFont getFont() {
        return gvtFont;
    }

    /**
     * Returns the FontRenderContext associated with this GlyphVector.
     */
    public FontRenderContext getFontRenderContext() {
        return awtGlyphVector.getFontRenderContext();
    }

    /**
     * Returns the glyphcode of the specified glyph.
     */
    public int getGlyphCode(int glyphIndex) {
        return awtGlyphVector.getGlyphCode(glyphIndex);
    }

    /**
     * Returns an array of glyphcodes for the specified glyphs.
     */
    public int[] getGlyphCodes(int beginGlyphIndex, int numEntries,
                               int[] codeReturn) {
        return awtGlyphVector.getGlyphCodes(beginGlyphIndex, numEntries,
                                            codeReturn);
    }

    /**
     * Returns the justification information for the glyph at the specified
     * index into this GlyphVector.
     */
    public GlyphJustificationInfo getGlyphJustificationInfo(int glyphIndex) {
        return awtGlyphVector.getGlyphJustificationInfo(glyphIndex);
    }

    /**
     *  Returns the logical bounds of the specified glyph within this
     *  GlyphVector.
     */
    public Shape getGlyphLogicalBounds(int glyphIndex) {
        if (glyphLogicalBounds[glyphIndex] == null && glyphVisible[glyphIndex]) {
            computeGlyphLogicalBounds();
        }
        return glyphLogicalBounds[glyphIndex];
    }

    /**
     * Calculates the logical bounds for each glyph. The logical bounds are
     * what is used for highlighting the glyphs when selected.
     */
    private void computeGlyphLogicalBounds() {

        GVTLineMetrics lineMetrics = gvtFont.getLineMetrics("By", awtGlyphVector.getFontRenderContext());
        float ascent = lineMetrics.getAscent() * scaleFactor;
        float descent = lineMetrics.getDescent() * scaleFactor;
        Shape[] tempLogicalBounds = new Shape[getNumGlyphs()];
        boolean[] rotated = new boolean[getNumGlyphs()];

        double maxWidth = -1;
        double maxHeight = -1;

        for (int i = 0; i < getNumGlyphs(); i++) {

            if (glyphVisible[i]) {

                AffineTransform glyphTransform = getGlyphTransform(i);

                if (glyphTransform == null) {

                    GVTGlyphMetrics glyphMetrics = getGlyphMetrics(i);

                    float glyphX = (float)(getGlyphPosition(i).getX());
                    float glyphY =  (float)getGlyphPosition(i).getY() - ascent;
                    float glyphWidth = glyphMetrics.getHorizontalAdvance();
                    float glyphHeight = glyphMetrics.getVerticalAdvance();

                    tempLogicalBounds[i] = new Rectangle2D.Double(glyphX, glyphY,
                                                                  glyphWidth, glyphHeight);
                    if (glyphWidth > maxWidth) maxWidth = glyphWidth;
                    if (glyphHeight > maxHeight) maxHeight = glyphHeight;
                    rotated[i] = false;

                } else {  // the glyph is transformed

                    Shape glyphOutline = awtGlyphVector.getGlyphOutline(i);
                    Rectangle2D glyphBounds = glyphOutline.getBounds2D();

                    // store three corner points so we can determine whether the glyph is rotated
                    Point2D p1 = new Point2D.Double(glyphBounds.getMinX(), glyphBounds.getMinY());
                    Point2D p2 = new Point2D.Double(glyphBounds.getMaxX(), glyphBounds.getMinY());
                    Point2D p3 = new Point2D.Double(glyphBounds.getMinX(), glyphBounds.getMaxY());

                    AffineTransform tr = AffineTransform.getTranslateInstance(getGlyphPosition(i).getX(),
                                                                              getGlyphPosition(i).getY());
                    tr.concatenate(glyphTransform);
                    tr.scale(scaleFactor, scaleFactor);
                    tempLogicalBounds[i] = tr.createTransformedShape(glyphBounds);

                    Point2D tp1 = new Point2D.Double();
                    Point2D tp2 = new Point2D.Double();
                    Point2D tp3 = new Point2D.Double();
                    tr.transform(p1, tp1);
                    tr.transform(p2, tp2);
                    tr.transform(p3, tp3);

                    if ((Math.abs(tp1.getX() - tp2.getX()) < 0.001
                            || Math.abs(tp1.getX() - tp3.getX()) < 0.001)
                            && (Math.abs(tp1.getY() - tp2.getY()) < 0.001
                            || Math.abs(tp1.getY() - tp3.getY()) < 0.001)) {
                            rotated[i] = false;
                    } else {
                            rotated[i] = true;
                    }
                    if (glyphBounds.isEmpty()) {
                        if (i > 0) {
                            // can't tell if rotated or not, make it the same as the
                            // previous glyph
                            rotated[i] = rotated[i-1];
                        } else {
                            rotated[i] = true;
                        }
                    }

                    Rectangle2D rectBounds = tempLogicalBounds[i].getBounds2D();
                    if (rectBounds.getWidth() > maxWidth) maxWidth = rectBounds.getWidth();
                    if (rectBounds.getHeight() > maxHeight) maxHeight = rectBounds.getHeight();
                }
            } else {
                // the glyph is not drawn
                tempLogicalBounds[i] = null;
            }
        }

        // if appropriate, join adjacent glyph logical bounds
        GeneralPath logicalBoundsPath = new GeneralPath();
        for (int i = 0; i < getNumGlyphs(); i++) {
            if (tempLogicalBounds[i] != null) {
                logicalBoundsPath.append(tempLogicalBounds[i], false);
            }
        }
        Rectangle2D fullBounds = logicalBoundsPath.getBounds2D();

        if (fullBounds.getHeight() < maxHeight*1.5) {
            // make all glyphs tops and bottoms the same as the full bounds
            for (int i = 0; i < getNumGlyphs(); i++) {
                // first make sure that the glyph logical bounds are not rotated
                if (!rotated[i] && tempLogicalBounds[i] != null) {
                    Rectangle2D glyphBounds = tempLogicalBounds[i].getBounds2D();
                    double x = glyphBounds.getMinX();
                    double width = glyphBounds.getWidth();
                    if (i < getNumGlyphs()-1 && tempLogicalBounds[i+1] != null) {
                        // make this glyph extend to the start of the next one
                        Rectangle2D nextGlyphBounds = tempLogicalBounds[i+1].getBounds2D();
                        if (nextGlyphBounds.getX() > x) { // going left to right
                            width = nextGlyphBounds.getX() - x;
                        } else {
                            double newGlyphX = nextGlyphBounds.getX() + nextGlyphBounds.getWidth();
                            width += (x - newGlyphX);
                            x = newGlyphX;
                        }
                    }
                    tempLogicalBounds[i] = new Rectangle2D.Double(x, fullBounds.getMinY(),
                                                                  width, fullBounds.getHeight());
                }
            }
        }
        if (fullBounds.getWidth() < maxWidth*1.5) {
            // make all glyphs left and right edges the same as the full bounds
            for (int i = 0; i < getNumGlyphs(); i++) {
                // first make sure that the glyph logical bounds are not rotated
                if (!rotated[i] && tempLogicalBounds[i] != null) {
                    Rectangle2D glyphBounds = tempLogicalBounds[i].getBounds2D();
                    double y = glyphBounds.getMinY();
                    double height = glyphBounds.getHeight();
                    if (i < getNumGlyphs()-1 && tempLogicalBounds[i+1] != null) {
                        // make this glyph extend to the start of the next one
                        Rectangle2D nextGlyphBounds = tempLogicalBounds[i+1].getBounds2D();
                        if (nextGlyphBounds.getY() > y) { // going top to bottom
                            height = nextGlyphBounds.getY() - y;
                        } else {
                            double newGlyphY = nextGlyphBounds.getY() + nextGlyphBounds.getHeight();
                            height += (y - newGlyphY);
                            y = newGlyphY;
                        }
                    }
                    tempLogicalBounds[i] = new Rectangle2D.Double(fullBounds.getMinX(), y,
                                                                  fullBounds.getWidth(), height);
                }
            }
        }

        for (int i = 0; i < getNumGlyphs(); i++) {
            glyphLogicalBounds[i] = tempLogicalBounds[i];
        }

    }



    /**
     * Returns the metrics of the glyph at the specified index into this
     * GVTGlyphVector.
     */
    public GVTGlyphMetrics getGlyphMetrics(int glyphIndex) {

        GVTLineMetrics lineMetrics = gvtFont.getLineMetrics("By", awtGlyphVector.getFontRenderContext());
        float ascent = lineMetrics.getAscent() * scaleFactor;
        float descent = lineMetrics.getDescent() * scaleFactor;

        GlyphMetrics gm = awtGlyphVector.getGlyphMetrics(glyphIndex);
        Rectangle2D gmBounds = gm.getBounds2D();
        Rectangle2D bounds = new Rectangle2D.Double(gmBounds.getX() * scaleFactor, gmBounds.getY() * scaleFactor,
                                                   gmBounds.getWidth() * scaleFactor, gmBounds.getHeight() * scaleFactor);
        return new GVTGlyphMetrics(gm.getAdvance()*scaleFactor, (ascent+descent), bounds, GlyphMetrics.STANDARD);
    }

    /**
     * Returns a Shape whose interior corresponds to the visual representation
     * of the specified glyph within this GlyphVector.
     */
    public Shape getGlyphOutline(int glyphIndex) {
        if (glyphOutlines[glyphIndex] == null) {
            Shape glyphOutline = awtGlyphVector.getGlyphOutline(glyphIndex);

            AffineTransform tr = AffineTransform.getTranslateInstance(getGlyphPosition(glyphIndex).getX(),
                                                                      getGlyphPosition(glyphIndex).getY());
            AffineTransform glyphTransform = getGlyphTransform(glyphIndex);
            if (glyphTransform != null) {
                tr.concatenate(glyphTransform);
            }
            tr.scale(scaleFactor, scaleFactor);
            glyphOutlines[glyphIndex] = tr.createTransformedShape(glyphOutline);
        }
        return glyphOutlines[glyphIndex];
    }

    /**
     * Returns the position of the specified glyph within this GlyphVector.
     */
    public Point2D getGlyphPosition(int glyphIndex) {
        return glyphPositions[glyphIndex];
    }

    /**
     * Returns the default position of the glyph. This will be the position that
     * is set when the performDefaultLayout method is run.
     */
    public Point2D getDefaultGlyphPosition(int glyphIndex) {
        if (defaultGlyphPositions[glyphIndex] == null) {
            performDefaultLayout();
        }
        return defaultGlyphPositions[glyphIndex];
    }


    /**
     * Returns an array of glyph positions for the specified glyphs
     */
    public float[] getGlyphPositions(int beginGlyphIndex, int numEntries,
                                     float[] positionReturn) {
        if (positionReturn == null) {
            positionReturn = new float[numEntries*2];
        }
        for (int i = beginGlyphIndex; i < (beginGlyphIndex+numEntries); i++) {
            Point2D glyphPos = getGlyphPosition(i);
            positionReturn[(i-beginGlyphIndex)*2] = (float)glyphPos.getX();
            positionReturn[(i-beginGlyphIndex)*2 + 1] = (float)glyphPos.getY();
        }
        return positionReturn;
    }

    /**
     * Gets the transform of the specified glyph within this GlyphVector.
     */
    public AffineTransform getGlyphTransform(int glyphIndex) {
        return glyphTransforms[glyphIndex];
    }

    /**
     * Returns the visual bounds of the specified glyph within the GlyphVector.
     */
    public Shape getGlyphVisualBounds(int glyphIndex) {
        if (glyphVisualBounds[glyphIndex] == null) {
            Shape glyphOutline = awtGlyphVector.getGlyphOutline(glyphIndex);
            Rectangle2D glyphBounds = glyphOutline.getBounds2D();
            AffineTransform tr = AffineTransform.getTranslateInstance(getGlyphPosition(glyphIndex).getX(),
                                                                      getGlyphPosition(glyphIndex).getY());
            AffineTransform glyphTransform = getGlyphTransform(glyphIndex);
            if (glyphTransform != null) {
                tr.concatenate(glyphTransform);
            }
            tr.scale(scaleFactor, scaleFactor);
            glyphVisualBounds[glyphIndex] = tr.createTransformedShape(glyphBounds);
        }
        return glyphVisualBounds[glyphIndex];
    }

    /**
     *  Returns the logical bounds of this GlyphVector.
     */
    public Rectangle2D getLogicalBounds() {
        if (logicalBounds == null) {
            GeneralPath logicalBoundsPath = new GeneralPath();
            for (int i = 0; i < getNumGlyphs(); i++) {
                Shape glyphLogicalBounds = getGlyphLogicalBounds(i);
                if (glyphLogicalBounds != null) {
                    logicalBoundsPath.append(glyphLogicalBounds, false);
                }
            }
            logicalBounds = logicalBoundsPath.getBounds2D();
        }
        return logicalBounds;
    }

    /**
     * Returns the number of glyphs in this GlyphVector.
     */
    public int getNumGlyphs() {
        return awtGlyphVector.getNumGlyphs();
    }

    /**
     * Returns a Shape whose interior corresponds to the visual representation
     * of this GlyphVector.
     */
    public Shape getOutline() {
        if (outline == null) {
            outline = new GeneralPath();
            for (int i = 0; i < getNumGlyphs(); i++) {
                if (glyphVisible[i]) {
                    Shape glyphOutline = getGlyphOutline(i);
                    outline.append(glyphOutline, false);
                }
            }
        }
        return outline;
    }

    /**
     * Returns a Shape whose interior corresponds to the visual representation
     * of this GlyphVector, offset to x, y.
     */
    public Shape getOutline(float x, float y) {
        Shape outline = getOutline();
        AffineTransform tr = AffineTransform.getTranslateInstance(x,y);
        outline = tr.createTransformedShape(outline);
        return outline;
    }

    /**
     * Returns the visual bounds of this GlyphVector The visual bounds is the
     * tightest rectangle enclosing all non-background pixels in the rendered
     * representation of this GlyphVector.
     */
    public Rectangle2D getVisualBounds() {
        Shape outline = getOutline();
        return outline.getBounds2D();
    }

    /**
     * Assigns default positions to each glyph in this GlyphVector.
     */
    public void performDefaultLayout() {
        awtGlyphVector.performDefaultLayout();
        outline = null;
        logicalBounds = null;
        float shiftLeft = 0;
        for (int i = 0; i < getNumGlyphs(); i++) {
            Point2D glyphPos = awtGlyphVector.getGlyphPosition(i);
            glyphPositions[i] = new Point2D.Float((float)((glyphPos.getX()-shiftLeft) * scaleFactor),
                                                  (float)(glyphPos.getY() * scaleFactor));
            defaultGlyphPositions[i] = glyphPositions[i];
            glyphTransforms[i] = null;
            glyphVisualBounds[i] = null;
            glyphLogicalBounds[i] = null;
            glyphOutlines[i] = null;

            // if c is a transparent arabic char then need to shift the following
            // glyphs left so that the current glyph is overwritten
            char c = ci.setIndex(i + ci.getBeginIndex());
            if (ArabicTextHandler.arabicCharTransparent(c)) {
                GlyphMetrics gm = awtGlyphVector.getGlyphMetrics(i);
                shiftLeft = gm.getAdvance();
            }
        }
    }

    /**
     * Sets the position of the specified glyph within this GlyphVector.
     */
    public void setGlyphPosition(int glyphIndex, Point2D newPos) {
        glyphPositions[glyphIndex] = new Point2D.Float((float)newPos.getX(), (float)newPos.getY());
        outline = null;
        logicalBounds = null;
        glyphVisualBounds[glyphIndex] = null;
        glyphLogicalBounds[glyphIndex] = null;
        glyphOutlines[glyphIndex] = null;
    }

    /**
     * Sets the transform of the specified glyph within this GlyphVector.
     */
    public void setGlyphTransform(int glyphIndex, AffineTransform newTX) {
        glyphTransforms[glyphIndex] = newTX;
        outline = null;
        logicalBounds = null;
        glyphVisualBounds[glyphIndex] = null;
        glyphLogicalBounds[glyphIndex] = null;
        glyphOutlines[glyphIndex] = null;
    }

    /**
     * Tells the glyph vector whether or not to draw the specified glyph.
     */
    public void setGlyphVisible(int glyphIndex, boolean visible) {
        glyphVisible[glyphIndex] = visible;
        outline = null;
        logicalBounds = null;
        glyphVisualBounds[glyphIndex] = null;
        glyphLogicalBounds[glyphIndex] = null;
        glyphOutlines[glyphIndex] = null;
    }

    /**
     * Returns the number of chars represented by the glyphs within the
     * specified range.
     * @param startGlyphIndex The index of the first glyph in the range.
     * @param endGlyphIndex The index of the last glyph in the range.
     * @return The number of chars.
     */
    public int getCharacterCount(int startGlyphIndex, int endGlyphIndex) {
        if (startGlyphIndex < 0) {
            startGlyphIndex = 0;
        }
        if (endGlyphIndex > getNumGlyphs()-1) {
            endGlyphIndex = getNumGlyphs()-1;
        }
        int charCount = 0;
        for (int i = ci.getBeginIndex() + startGlyphIndex; i <= ci.getBeginIndex() + endGlyphIndex; i++) {
            char c = ci.setIndex(i);
            if (ArabicTextHandler.isLigature(c)) {
                charCount += ArabicTextHandler.getNumChars(c);
            } else {
                charCount++;
            }
        }
        return charCount;
    }


    /**
     * Draws this glyph vector.
     */
    public void draw(Graphics2D graphics2D, GraphicsNodeRenderContext context,
                     AttributedCharacterIterator aci) {

        aci.first();
        Shape outline = getOutline();

        // check if we need to fill this glyph
        Paint paint = (Paint) aci.getAttribute(TextAttribute.FOREGROUND);
        if (paint != null) {
            graphics2D.setPaint(paint);
            graphics2D.fill(outline);
        }

        // check if we need to draw the outline of this glyph
        Stroke stroke = (Stroke) aci.getAttribute(
            GVTAttributedCharacterIterator.TextAttribute.STROKE);
        paint = (Paint) aci.getAttribute(
            GVTAttributedCharacterIterator.TextAttribute.STROKE_PAINT);
        if (stroke != null && paint != null) {
            graphics2D.setStroke(stroke);
            graphics2D.setPaint(paint);
            graphics2D.draw(outline);
        }

    }
}
