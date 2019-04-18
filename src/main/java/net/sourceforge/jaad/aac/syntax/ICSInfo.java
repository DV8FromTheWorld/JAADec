package net.sourceforge.jaad.aac.syntax;

import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.DecoderConfig;
import net.sourceforge.jaad.aac.Profile;
import net.sourceforge.jaad.aac.SampleFrequency;
import net.sourceforge.jaad.aac.tools.ICPrediction;
import net.sourceforge.jaad.aac.tools.LTPrediction;
import java.util.Arrays;

public class ICSInfo implements Constants, ScaleFactorBands {

	public static final int WINDOW_SHAPE_SINE = 0;
	public static final int WINDOW_SHAPE_KAISER = 1;
	public static final int PREVIOUS = 0;
	public static final int CURRENT = 1;

	public static enum WindowSequence {

		ONLY_LONG_SEQUENCE,
		LONG_START_SEQUENCE,
		EIGHT_SHORT_SEQUENCE,
		LONG_STOP_SEQUENCE;

		public static WindowSequence forInt(int i) throws AACException {
			WindowSequence w;
			switch(i) {
				case 0:
					w = ONLY_LONG_SEQUENCE;
					break;
				case 1:
					w = LONG_START_SEQUENCE;
					break;
				case 2:
					w = EIGHT_SHORT_SEQUENCE;
					break;
				case 3:
					w = LONG_STOP_SEQUENCE;
					break;
				default:
					throw new AACException("unknown window sequence type");
			}
			return w;
		}
	}
	private final int frameLength;
	private WindowSequence windowSequence;
	private int[] windowShape;
	private int maxSFB;
	//prediction
	private boolean predictionDataPresent;
	private ICPrediction icPredict;
	private final LTPrediction ltPredict;
	//windows/sfbs
	private int windowCount;
	private int windowGroupCount;
	private int[] windowGroupLength;
	private int swbCount;
	private int[] swbOffsets;

	public ICSInfo(DecoderConfig config) {
		this.frameLength = config.getFrameLength();
		windowShape = new int[2];
		windowSequence = WindowSequence.ONLY_LONG_SEQUENCE;
		windowGroupLength = new int[MAX_WINDOW_GROUP_COUNT];

		if(LTPrediction.isLTPProfile(config.getProfile()))
			ltPredict = new LTPrediction(frameLength);
		else
			ltPredict = null;
	}

	/* ========== decoding ========== */
	public void decode(BitStream in, DecoderConfig conf, boolean commonWindow) throws AACException {
		final SampleFrequency sf = conf.getSampleFrequency();
		if(sf.equals(SampleFrequency.SAMPLE_FREQUENCY_NONE)) throw new AACException("invalid sample frequency");

		in.skipBit(); //reserved
		windowSequence = WindowSequence.forInt(in.readBits(2));
		windowShape[PREVIOUS] = windowShape[CURRENT];
		windowShape[CURRENT] = in.readBit();

		windowGroupCount = 1;
		windowGroupLength[0] = 1;

		if(windowSequence.equals(WindowSequence.EIGHT_SHORT_SEQUENCE)) {
			maxSFB = in.readBits(4);
			int i;
			for(i = 0; i<7; i++) {
				if(in.readBool()) windowGroupLength[windowGroupCount-1]++;
				else {
					windowGroupCount++;
					windowGroupLength[windowGroupCount-1] = 1;
				}
			}
			windowCount = 8;
			swbOffsets = SWB_OFFSET_SHORT_WINDOW[sf.getIndex()];
			swbCount = SWB_SHORT_WINDOW_COUNT[sf.getIndex()];
		}
		else {
			maxSFB = in.readBits(6);
			windowCount = 1;
			swbOffsets = SWB_OFFSET_LONG_WINDOW[sf.getIndex()];
			swbCount = SWB_LONG_WINDOW_COUNT[sf.getIndex()];
			predictionDataPresent = in.readBool();
			if(predictionDataPresent) readPredictionData(in, conf.getProfile(), sf, commonWindow);
		}
	}

	private void readPredictionData(BitStream in, Profile profile, SampleFrequency sf, boolean commonWindow) throws AACException {
		switch(profile) {
			case AAC_MAIN:
				if(icPredict==null) icPredict = new ICPrediction();
				icPredict.decode(in, maxSFB, sf);
				break;
			case AAC_LTP:
				ltPredict.decode(in, this, profile);

				break;
			case ER_AAC_LTP:
				if(!commonWindow) {
					ltPredict.decode(in, this, profile);
				}
				break;
			default:
				throw new AACException("unexpected profile for LTP: "+profile);
		}
	}

	/* =========== gets ============ */
	public int getMaxSFB() {
		return maxSFB;
	}

	public int getSWBCount() {
		return swbCount;
	}

	public int[] getSWBOffsets() {
		return swbOffsets;
	}

	public int getSWBOffsetMax() {
		return swbOffsets[swbCount];
	}

	public int getWindowCount() {
		return windowCount;
	}

	public int getWindowGroupCount() {
		return windowGroupCount;
	}

	public int getWindowGroupLength(int g) {
		return windowGroupLength[g];
	}

	public WindowSequence getWindowSequence() {
		return windowSequence;
	}

	public boolean isEightShortFrame() {
		return windowSequence.equals(WindowSequence.EIGHT_SHORT_SEQUENCE);
	}

	public int getWindowShape(int index) {
		return windowShape[index];
	}

	public boolean isICPredictionPresent() {
		return predictionDataPresent;
	}

	public ICPrediction getICPrediction() {
		return icPredict;
	}

	public LTPrediction getLTPrediction() {
		return ltPredict;
	}


	public void unsetPredictionSFB(int sfb) {
		if(predictionDataPresent) icPredict.setPredictionUnused(sfb);
		if(ltPredict!=null) ltPredict.setPredictionUnused(sfb);
	}

	public void setData(BitStream in, DecoderConfig conf, ICSInfo info) throws AACException {
		windowSequence = WindowSequence.valueOf(info.windowSequence.name());
		windowShape[PREVIOUS] = windowShape[CURRENT];
		windowShape[CURRENT] = info.windowShape[CURRENT];
		maxSFB = info.maxSFB;
		predictionDataPresent = info.predictionDataPresent;
		if(predictionDataPresent) icPredict = info.icPredict;

		windowCount = info.windowCount;
		windowGroupCount = info.windowGroupCount;
		windowGroupLength = Arrays.copyOf(info.windowGroupLength, info.windowGroupLength.length);
		swbCount = info.swbCount;
		swbOffsets = Arrays.copyOf(info.swbOffsets, info.swbOffsets.length);

		if(predictionDataPresent) {
			ltPredict.decode(in, this, conf.getProfile());
		}
	}
}
