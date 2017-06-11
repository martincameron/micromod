
/* ibxm/ac mod/xm/s3m replay (c)mumart@gmail.com */

const char *IBXM_VERSION;

struct data {
	char *buffer;
	int length;
};

struct sample {
	char name[ 32 ];
	int loop_start, loop_length;
	short volume, panning, rel_note, fine_tune, *data;
};

struct envelope {
	char enabled, sustain, looped, num_points;
	short sustain_tick, loop_start_tick, loop_end_tick;
	short points_tick[ 16 ], points_ampl[ 16 ];
};

struct instrument {
	int num_samples, vol_fadeout;
	char name[ 32 ], key_to_sample[ 97 ];
	char vib_type, vib_sweep, vib_depth, vib_rate;
	struct envelope vol_env, pan_env;
	struct sample *samples;
};

struct pattern {
	int num_channels, num_rows;
	char *data;
};

struct module {
	char name[ 32 ];
	int num_channels, num_instruments;
	int num_patterns, sequence_len, restart_pos;
	int default_gvol, default_speed, default_tempo, c2_rate, gain;
	int linear_periods, fast_vol_slides;
	unsigned char *default_panning, *sequence;
	struct pattern *patterns;
	struct instrument *instruments;
};

struct note {
	unsigned char key, instrument, volume, effect, param;
};

struct channel {
	struct replay *replay;
	struct instrument *instrument;
	struct sample *sample;
	struct note note;
	int id, key_on, random_seed, pl_row;
	int sample_off, sample_idx, sample_fra, freq, ampl, pann;
	int volume, panning, fadeout_vol, vol_env_tick, pan_env_tick;
	int period, porta_period, retrig_count, fx_count, av_count;
	int porta_up_param, porta_down_param, tone_porta_param, offset_param;
	int fine_porta_up_param, fine_porta_down_param, xfine_porta_param;
	int arpeggio_param, vol_slide_param, gvol_slide_param, pan_slide_param;
	int fine_vslide_up_param, fine_vslide_down_param;
	int retrig_volume, retrig_ticks, tremor_on_ticks, tremor_off_ticks;
	int vibrato_type, vibrato_phase, vibrato_speed, vibrato_depth;
	int tremolo_type, tremolo_phase, tremolo_speed, tremolo_depth;
	int tremolo_add, vibrato_add, arpeggio_add;
};

struct replay {
	int sample_rate, interpolation, global_vol;
	int seq_pos, break_pos, row, next_row, tick;
	int speed, tempo, pl_count, pl_chan;
	int *ramp_buf;
	char **play_count;
	struct channel *channels;
	struct module *module;
};

/* Allocate and initialize a module from the specified data, returns NULL on error.
   Message should point to a 64-character buffer to receive error messages. */
struct module* module_load( struct data *data, char *message );
/* Deallocate the specified module. */
void dispose_module( struct module *module );
/* Allocate and initialize a replay with the specified module and sampling rate. */
struct replay* new_replay( struct module *module, int sample_rate, int interpolation );
/* Deallocate the specified replay. */
void dispose_replay( struct replay *replay );
/* Returns the song duration in samples at the current sampling rate. */
int replay_calculate_duration( struct replay *replay );
/* Seek to approximately the specified sample position.
   The actual sample position reached is returned. */
int replay_seek( struct replay *replay, int sample_pos );
/* Set the pattern in the sequence to play. The tempo is reset to the default. */
void replay_set_sequence_pos( struct replay *replay, int pos );
/* Generates audio and returns the number of stereo samples written into mix_buf. */
int replay_get_audio( struct replay *replay, int *mix_buf );
/* Returns the length of the output buffer required by replay_get_audio(). */
int calculate_mix_buf_len( int sample_rate );
