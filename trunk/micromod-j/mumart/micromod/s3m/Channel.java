
package mumart.micromod.s3m;

public class Channel {
	private static final int
		FP_SHIFT = 15,
		FP_ONE = 1 << FP_SHIFT,
		FP_MASK = FP_ONE - 1;

	private static final short[] period_table = {
		27392, 25855, 24403, 23034, 21741, 20521, 19369, 18282, 17256, 16287, 15373, 14510,
		13696, 12927, 12202, 11517, 10871, 10260,  9685,  9141,  8628,  8144,  7687,  7255,
		 6848,  6464,  6101,  5758,  5435,  5130,  4842,  4570,  4314,  4072,  3843,  3628,
		 3424,  3232,  3050,  2879,  2718,  2565,  2421,  2285,  2157,  2036,  1922,  1814,
		 1712,  1616,  1525,  1440,  1359,  1283,  1211,  1143,  1078,  1018,   961,   907,
		 856,    808,   763,   720,   679,   641,   605,   571,   539,   509,   480,   453,
		 428,    404,   381,   360,   340,   321,   303,   286,   270,   254,   240,   227,
		 214,    202,   191,   180,   170,   160,   151,   143,   135,   127,   120,   113,
		 107,    101,    95,    90,    85,    80,    76,    71,    67,    64,    60,    57
	};

	private static final short[] arp_tuning = {
		4096, 4340, 4598, 4871, 5161, 5468, 5793, 6137,
		6502, 6889, 7298, 7732, 8192, 8679, 9195, 9742
	};

	private static final short[] sine_table = {
		   0,  24,  49,  74,  97, 120, 141, 161, 180, 197, 212, 224, 235, 244, 250, 253,
		 255, 253, 250, 244, 235, 224, 212, 197, 180, 161, 141, 120,  97,  74,  49,  24
	};

	private Module module;
	private GlobalVol global_vol;
	private int note_key, note_vol, note_effect, note_param;
	private int note_ins, instrument, assigned;
	private int sample_idx, sample_fra, step;
	private int volume, panning, c2_rate, ampl;
	private int period, porta_period, retrig_count, fx_count;
	private int porta_param, tone_porta_param, vslide_param, offset_param;
	private int retrig_volume, retrig_ticks, tremor_on_ticks, tremor_off_ticks;
	private int vibrato_type, vibrato_phase, vibrato_speed, vibrato_depth;
	private int tremolo_type, tremolo_phase, tremolo_speed, tremolo_depth;
	private int tremolo_add, vibrato_add, arpeggio_add;
	private int id, sample_rate, gain, random_seed;
	public int pl_row;
	
	public Channel( Module module, int id, int sample_rate, GlobalVol global_vol ) {
		this.module = module;
		this.id = random_seed = id;
		this.sample_rate = sample_rate;
		this.global_vol = global_vol;
		panning = module.default_panning[ id ];
	}
	
	public void resample( int[] out_buf, int offset, int length, boolean interpolate ) {
		if( ampl <= 0 ) return;
		int l_ampl = ampl * panning >> 8;
		int r_ampl = ampl * ( 255 - panning ) >> 8;
		int sam_idx = sample_idx;
		int sam_fra = sample_fra;
		int step = this.step;
		Instrument ins = module.instruments[ instrument ];
		int loop_len = ins.loop_length;
		int loop_ep1 = ins.loop_start + loop_len;
		byte[] sample_data = ins.sample_data;
		int out_idx = offset << 1;
		int out_ep1 = offset + length << 1;
		if( interpolate ) {
			while( out_idx < out_ep1 ) {
				if( sam_idx >= loop_ep1 ) {
					if( loop_len <= 1 ) break;
					while( sam_idx >= loop_ep1 ) sam_idx -= loop_len;
				}
				int c = sample_data[ sam_idx ];
				int m = sample_data[ sam_idx + 1 ] - c;
				int y = ( m * sam_fra >> FP_SHIFT - 8 ) + ( c << 8 );
				out_buf[ out_idx++ ] += y * l_ampl >> FP_SHIFT;
				out_buf[ out_idx++ ] += y * r_ampl >> FP_SHIFT;
				sam_fra += step;
				sam_idx += sam_fra >> FP_SHIFT;
				sam_fra &= FP_MASK;
			}
		} else {
			while( out_idx < out_ep1 ) {
				if( sam_idx >= loop_ep1 ) {
					if( loop_len <= 1 ) break;
					while( sam_idx >= loop_ep1 ) sam_idx -= loop_len;
				}
				int y = sample_data[ sam_idx ];
				out_buf[ out_idx++ ] += y * l_ampl >> FP_SHIFT - 8;
				out_buf[ out_idx++ ] += y * r_ampl >> FP_SHIFT - 8;
				sam_fra += step;
				sam_idx += sam_fra >> FP_SHIFT;
				sam_fra &= FP_MASK;
			}
		}
	}

	public void update_sample_idx( int length ) {
		sample_fra += step * length;
		sample_idx += sample_fra >> FP_SHIFT;
		Instrument ins = module.instruments[ instrument ];
		int loop_start = ins.loop_start;
		int loop_length = ins.loop_length;
		int loop_offset = sample_idx - loop_start;
		if( loop_offset > 0 ) {
			sample_idx = loop_start;
			if( loop_length > 1 ) sample_idx += loop_offset % loop_length;
		}
		sample_fra &= FP_MASK;
	}

	public void row( Note note ) {
		note_key = note.key;
		note_ins = note.instrument;
		note_vol = note.volume;
		note_effect = note.effect;
		note_param = note.param;
		vibrato_add = tremolo_add = arpeggio_add = fx_count = 0;
		retrig_count++;
		if( note_effect != 0x10D ) trigger();
		switch( note_effect ) {
			case 0x04: /* Volume Slide.*/
				if( note_param > 0 ) vslide_param = note_param;
				volume_slide();
				break;
			case 0x05: /* Portamento Down.*/
				if( note_param > 0 ) porta_param = note_param;
				portamento_down();
				break;
			case 0x06: /* Portamento Up.*/
				if( note_param > 0 ) porta_param = note_param;
				portamento_up();
				break;
			case 0x07: /* Tone Portamento.*/
				if( note_param > 0 ) tone_porta_param = note_param;
				break;
			case 0x08: /* Vibrato.*/
				if( ( note_param >> 4 ) > 0 ) vibrato_speed = note_param >> 4;
				if( ( note_param & 0xF ) > 0 ) vibrato_depth = note_param & 0xF;
				vibrato( false );
				break;
			case 0x09: /* Tremor.*/
				if( ( note_param >> 4 ) > 0 ) tremor_on_ticks = note_param >> 4;
				if( ( note_param & 0xF ) > 0 ) tremor_off_ticks = note_param & 0xF;
				tremor();
				break;
			case 0x0B: /* Vibrato + Volume Slide.*/
				if( note_param > 0 ) vslide_param = note_param;
				vibrato( false );
				volume_slide();
				break;
			case 0x0C: /* Tone Portamento + Volume Slide.*/
				if( note_param > 0 ) vslide_param = note_param;
				volume_slide();
				break;
			case 0x0F: /* Set Sample Offset.*/
				if( note_param > 0 ) offset_param = note_param;
				sample_idx = offset_param << 8;
				sample_fra = 0;
				break;
			case 0x11: /* Retrig + Volume Slide.*/
				if( ( note_param >> 4 ) > 0 ) retrig_volume = note_param >> 4;
				if( ( note_param & 0xF ) > 0 ) retrig_ticks = note_param & 0xF;
				retrig_vol_slide();
				break;
			case 0x12: /* Tremolo.*/
				if( ( note_param >> 4 ) > 0 ) tremolo_speed = note_param >> 4;
				if( ( note_param & 0xF ) > 0 ) tremolo_depth = note_param & 0xF;
				tremolo();
				break;
			case 0x15: /* Fine Vibrato.*/
				if( ( note_param >> 4 ) > 0 ) vibrato_speed = note_param >> 4;
				if( ( note_param & 0xF ) > 0 ) vibrato_depth = note_param & 0xF;
				vibrato( true );
				break;
			case 0x16: /* Set Global Volume.*/
				global_vol.volume = ( note_param > 64 ) ? 64 : note_param;
				break;
			case 0x101: /* Glissando.*/
				break;
			case 0x102: /* Set Finetune.*/
				break;
			case 0x103: /* Set Vibrato Waveform.*/
				if( note_param < 8 ) vibrato_type = note_param;
				break;
			case 0x104: /* Set Tremolo Waveform.*/
				if( note_param < 8 ) tremolo_type = note_param;
				break;
			case 0x108: /* Set Panning.*/
				panning = note_param * 17;
				break;
			case 0x10C: /* Note Cut.*/
				if( note_param <= 0 ) volume = 0;
				break;
			case 0x10D: /* Note Delay.*/
				if( note_param <= 0 ) trigger();
				break;
		}
		update_frequency();
	}
	
	public void tick() {
		fx_count++;
		retrig_count++;
		switch( note_effect ) {
			case 0x04: /* Volume Slide.*/
				volume_slide();
				break;
			case 0x05: /* Portamento Down.*/
				portamento_down();
				break;
			case 0x06: /* Portamento Up.*/
				portamento_up();
				break;
			case 0x07: /* Tone Portamento.*/
				tone_portamento();
				break;
			case 0x08: /* Vibrato.*/
				vibrato_phase += vibrato_speed;
				vibrato( false );
				break;
			case 0x09: /* Tremor.*/
				tremor();
				break;
			case 0x0A: /* Arpeggio.*/
				if( fx_count > 2 ) fx_count = 0;
				if( fx_count == 0 ) arpeggio_add = 0;
				if( fx_count == 1 ) arpeggio_add = note_param >> 4;
				if( fx_count == 2 ) arpeggio_add = note_param & 0xF;
				break;
			case 0x0B: /* Vibrato + Volume Slide.*/
				vibrato_phase += vibrato_speed;
				vibrato( false );
				volume_slide();
				break;
			case 0x0C: /* Tone Portamento + Volume Slide.*/
				tone_portamento();
				volume_slide();
				break;
			case 0x11: /* Retrig + Volume Slide.*/
				retrig_vol_slide();
				break;
			case 0x12: /* Tremolo.*/
				tremolo_phase += tremolo_speed;
				tremolo();
				break;
			case 0x15: /* Fine Vibrato. */
				vibrato_phase += vibrato_speed;
				vibrato( true );
				break;
			case 0x10C: /* Note Cut.*/
				if( note_param == fx_count ) volume = 0;
				break;
			case 0x10D: /* Note Delay.*/
				if( note_param == fx_count ) trigger();
				break;
		}
		if( note_effect > 0 ) update_frequency();
	}
	
	private void update_frequency() {
		int period, freq, volume;
		period = this.period + vibrato_add;
		if( period < 56 ) period = 56;
		freq = 8363 * 428 / period;
		freq = ( freq * arp_tuning[ arpeggio_add ] >> 12 ) & 0xFFFF;
		step = ( freq << FP_SHIFT ) / ( sample_rate >> 2 );
		volume = this.volume + tremolo_add;
		if( volume > 64 ) volume = 64;
		if( volume < 0 ) volume = 0;
		volume = volume * global_vol.volume * FP_ONE >> 12;
		ampl = volume * module.gain >> 7;
	}
	
	private void trigger() {
		if( note_ins > 0 && note_ins <= module.num_instruments ) {
			assigned = note_ins;
			Instrument assigned_ins = module.instruments[ assigned ];
			c2_rate = assigned_ins.c2_rate & 0xFFFF;
			volume = assigned_ins.volume >= 64 ? 64 : assigned_ins.volume & 0x3F;
		}
		if( note_vol >= 0x10 )
			volume = note_vol >= 0x50 ? 64 : note_vol - 0x10;
		if( note_key > 0 ) {
			if( note_key > 108 ) {
				volume = 0;
			} else {
				int period = period_table[ note_key - 1 ];
				int c2_rate = ( this.c2_rate > 0 ) ? this.c2_rate : 8363;
				porta_period = 8363 * period / c2_rate;
				if( note_effect != 0x07 && note_effect != 0x0C ) {
					instrument = assigned;
					this.period = porta_period;
					sample_idx = sample_fra = 0;
					if( vibrato_type < 4 ) vibrato_phase = 0;
					if( tremolo_type < 4 ) tremolo_phase = 0;
					retrig_count = 0;
				}
			}
		}
	}

	private void volume_slide() {
		int up = vslide_param >> 4;
		int down = vslide_param & 0xF;
		if( down == 0xF && up > 0 ) { /* Fine slide up.*/
			if( fx_count == 0 ) volume += up;
		} else if( up == 0xF && down > 0 ) { /* Fine slide down.*/
			if( fx_count == 0 ) volume -= down;
		} else if( fx_count > 0 || module.fast_vol_slides ) /* Normal.*/
			volume += up - down;
		if( volume > 64 ) volume = 64;
		if( volume < 0 ) volume = 0;
	}

	private void portamento_up() {
		int param = porta_param;
		if( ( param & 0xF0 ) == 0xE0 ) { /* Extra-fine porta.*/
			if( fx_count == 0 ) period -= param & 0xF;
		} else if( ( param & 0xF0 ) == 0xF0 ) { /* Fine porta.*/
			if( fx_count == 0 ) period -= ( param & 0x0F ) << 2;
		} else if( fx_count > 0 ) /* Normal porta.*/
			period -= param << 2;
	}

	private void portamento_down() {
		int param = porta_param;
		if( ( param & 0xF0 ) == 0xE0 ) { /* Extra-fine porta.*/
			if( fx_count == 0 ) period += param & 0xF;
		} else if( ( param & 0xF0 ) == 0xF0 ) { /* Fine porta.*/
			if( fx_count == 0 ) period += ( param & 0x0F ) << 2;
		} else if( fx_count > 0 ) /* Normal porta.*/
			period += param << 2;
	}

	private void tone_portamento() {
		int source = period;
		int dest = porta_period;
		if( source < dest ) {
			source += tone_porta_param << 2;
			if( source > dest ) source = dest;
		} else if( source > dest ) {
			source -= tone_porta_param << 2;
			if( source < dest ) source = dest;
		}
		period = source;
	}

	private void vibrato( boolean fine ) {
		vibrato_add = waveform( vibrato_phase, vibrato_type ) * vibrato_depth >> ( fine ? 7 : 5 );
	}
	
	private void tremolo() {
		tremolo_add = waveform( tremolo_phase, tremolo_type ) * tremolo_depth >> 6;
	}

	private int waveform( int phase, int type ) {
		int amplitude = 0;
		switch( type & 0x3 ) {
			case 0: /* Sine. */
				amplitude = sine_table[ phase & 0x1F ];
				if( ( phase & 0x20 ) > 0 ) amplitude = -amplitude;
				break;
			case 1: /* Saw Down. */
				amplitude = 255 - ( ( ( phase + 0x20 ) & 0x3F ) << 3 );
				break;
			case 2: /* Square. */
				amplitude = ( phase & 0x20 ) > 0 ? 255 : -255;
				break;
			case 3: /* Random. */
				amplitude = random_seed - 255;
				random_seed = ( random_seed * 65 + 17 ) & 0x1FF;
				break;
		}
		return amplitude;
	}

	private void tremor() {
		if( retrig_count >= tremor_on_ticks ) tremolo_add = -64;
		if( retrig_count >= ( tremor_on_ticks + tremor_off_ticks ) )
			tremolo_add = retrig_count = 0;
	}

	private void retrig_vol_slide() {
		if( retrig_count >= retrig_ticks ) {
			retrig_count = sample_idx = sample_fra = 0;
			switch( retrig_volume ) {
				case 0x1: volume -=  1; break;
				case 0x2: volume -=  2; break;
				case 0x3: volume -=  4; break;
				case 0x4: volume -=  8; break;
				case 0x5: volume -= 16; break;
				case 0x6: volume -= volume / 3; break;
				case 0x7: volume >>= 1; break;
				case 0x8: /* ? */ break;
				case 0x9: volume +=  1; break;
				case 0xA: volume +=  2; break;
				case 0xB: volume +=  4; break;
				case 0xC: volume +=  8; break;
				case 0xD: volume += 16; break;
				case 0xE: volume += volume >> 1; break;
				case 0xF: volume <<= 1; break;
			}
			if( volume <  0 ) volume = 0;
			if( volume > 64 ) volume = 64;
		}
	}
}
