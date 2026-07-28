package com.megacrit.cardcrawl.helpers.controller;

import com.badlogic.gdx.math.Vector3;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.screens.options.RemapInputElement;
import net.java.games.input.ControllerEvent;
import net.java.games.input.ControllerListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Controller;

public class CInputListener
        implements ControllerListener {
    private static final Logger logger = LogManager.getLogger(CInputListener.class.getName());
    private static final float DEADZONE = 0.5F;
    private float[] axisValues = new float[] {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F };
    public static boolean remapping = false;
    public static RemapInputElement element = null;

    public static void listenForRemap(RemapInputElement e) {
        element = e;
        remapping = true;
    }

    public void connected(Controller controller) {
        logger.info("CONNECTED: " + controller.getName());
    }

    public void disconnected(Controller controller) {
        logger.info("DISCONNECTED: " + controller.getName());
    }

    public boolean buttonDown(Controller controller, int buttonCode) {
        if (!Settings.CONTROLLER_ENABLED) {
            return false;
        }

        if (remapping && element.cAction != null) {
            if (element.listener.willRemapController(element, element.cAction.keycode, buttonCode)) {
                element.cAction.remap(buttonCode);
            }
            remapping = false;
            element.hasInputFocus = false;
            InputHelper.regainInputFocus();
            CInputHelper.regainInputFocus();
            element = null;
            return false;
        }

        if (!Settings.isControllerMode) {
            Settings.isControllerMode = true;
            Settings.isTouchScreen = false;
        }
        CInputHelper.listenerPress(buttonCode);
        return false;
    }

    public boolean buttonUp(Controller controller, int buttonCode) {
        CInputHelper.listenerRelease(buttonCode);
        return false;
    }

    public boolean axisMoved(Controller controller, int axisCode, float value) {
        if (!Settings.CONTROLLER_ENABLED) {
            return false;
        }

        if (remapping && element.cAction != null) {
            if (value > 0.5F && this.axisValues[axisCode] < 0.5F) {
                if (element.listener.willRemapController(element, element.cAction.keycode, 1000 + axisCode)) {
                    element.cAction.remap(1000 + axisCode);
                }
                remapping = false;
                element.hasInputFocus = false;
                InputHelper.regainInputFocus();
                CInputHelper.regainInputFocus();
                element = null;
                return false;
            }
            if (value < -0.5F && this.axisValues[axisCode] > -0.5F) {
                if (element.listener.willRemapController(element, element.cAction.keycode, -1000 - axisCode)) {
                    element.cAction.remap(-1000 - axisCode);
                }
                remapping = false;
                element.hasInputFocus = false;
                InputHelper.regainInputFocus();
                CInputHelper.regainInputFocus();
                element = null;
                return false;
            }
        }

        if (value > 0.5F && this.axisValues[axisCode] < 0.5F) {
            CInputHelper.listenerPress(1000 + axisCode);
            if (!Settings.isControllerMode) {
                Settings.isControllerMode = true;
                Settings.isTouchScreen = false;
                logger.info("Entering controller mode: AXIS moved");
            }
        } else if (value < -0.5F && this.axisValues[axisCode] > -0.5F) {
            CInputHelper.listenerPress(-1000 - axisCode);
            if (!Settings.isControllerMode) {
                Settings.isControllerMode = true;
                Settings.isTouchScreen = false;
                logger.info("Entering controller mode: AXIS moved");
            }
        }
        this.axisValues[axisCode] = value;

        return false;
    }

    public boolean povMoved(Controller controller, int povCode, PovDirection value) {
        if (!Settings.CONTROLLER_ENABLED) {
            return false;
        }

        if (remapping && element.cAction != null) {
            int code = -2000;
            switch (value) {
                case north:
                    code = -2000;
                    break;
                case south:
                    code = 2000;
                    break;
                case northWest:
                case southWest:
                case west:
                    code = -2001;
                    break;
                case northEast:
                case southEast:
                case east:
                    code = 2001;
                    break;
                default:
                    code = -2000;
                    break;
            }

            if (element.listener.willRemapController(element, element.cAction.keycode, code)) {
                element.cAction.remap(code);
            }
            remapping = false;
            element.hasInputFocus = false;
            InputHelper.regainInputFocus();
            CInputHelper.regainInputFocus();
            element = null;
            return false;
        }

        if (!Settings.isControllerMode) {
            Settings.isControllerMode = true;
            Settings.isTouchScreen = false;
            logger.info("Entering controller mode: D-Pad pressed");
        }

        switch (value) {
            case north:
                CInputHelper.listenerPress(-2000);
                break;
            case south:
                CInputHelper.listenerPress(2000);
                break;
            case northWest:
            case southWest:
            case west:
                CInputHelper.listenerPress(-2001);
                break;
            case northEast:
            case southEast:
            case east:
                CInputHelper.listenerPress(2001);
                break;
        }

        return false;
    }

    public boolean xSliderMoved(Controller controller, int sliderCode, boolean value) {
        return false;
    }

    public boolean ySliderMoved(Controller controller, int sliderCode, boolean value) {
        return false;
    }

    public boolean accelerometerMoved(Controller controller, int accelerometerCode, Vector3 value) {
        return false;
    }

    @Override
    public void controllerRemoved(ControllerEvent controllerEvent) {

    }

    @Override
    public void controllerAdded(ControllerEvent controllerEvent) {

    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\controller
 * \CInputListener.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

