/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.refimpl.bridge;

import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.parser.ParserFactory;
import org.apache.batik.util.UnitProcessor;

import org.w3c.dom.css.CSSStyleDeclaration;
import org.w3c.dom.css.CSSPrimitiveValue;
import org.w3c.dom.svg.SVGElement;
import org.w3c.dom.svg.SVGSVGElement;

/**
 * The default unit processor context.
 *
 * @author <a href="mailto:Thierry.Kormann@sophia.inria.fr">Thierry Kormann</a>
 * @version $Id$
 */
public class DefaultUnitProcessorContext implements UnitProcessor.Context {

    protected BridgeContext ctx;
    protected CSSStyleDeclaration cssDecl;

    public DefaultUnitProcessorContext(BridgeContext ctx,
                                       CSSStyleDeclaration cssDecl) {
        this.ctx = ctx;
        this.cssDecl = cssDecl;
    }

    /**
     * Returns the pixel to mm factor.
     */
    public float getPixelToMM() {
        return ctx.getUserAgent().getPixelToMM();
    }

    /**
     * Returns the parser factory.
     */
    public ParserFactory getParserFactory() {
        return ctx.getParserFactory();
    }

    /**
     * Returns the font-size medium value in pt.
     */
    public float getMediumFontSize() {
        return 10;
    }

    /**
     * Returns the font-size value.
     */
    public CSSPrimitiveValue getFontSize(SVGElement e) {
        return UnitProcessor.getFontSize(e, cssDecl);
    }

    /**
     * Returns the x-height value.
     */
    public float getXHeight(SVGElement e) {
        return 0.5f;
    }

    /**
     * Returns the viewport to use to compute the percentages and the units.
     */
    public SVGSVGElement getViewport() {
        return ctx.getCurrentViewport();
    }
}
