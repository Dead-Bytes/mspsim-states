"""
Hyperparameter Experimentation Script for Energy Harvesting Prediction
Systematically tests different ML model configurations and logs results
"""

import numpy as np
import pandas as pd
import json
import time
from datetime import datetime
import warnings
warnings.filterwarnings('ignore')

try:
    import tensorflow as tf
    from tensorflow import keras
    from sklearn.model_selection import train_test_split
    from sklearn.preprocessing import StandardScaler
    from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
    HAS_DEPENDENCIES = True
except ImportError as e:
    print(f"Missing dependencies: {e}")
    HAS_DEPENDENCIES = False


class HyperparameterExperiment:
    """Manages hyperparameter experimentation for energy prediction models"""

    def __init__(self, data_path, output_dir='outputs'):
        self.data_path = data_path
        self.output_dir = output_dir
        self.results = []
        self.experiment_id = datetime.now().strftime("%Y%m%d_%H%M%S")

    def load_and_preprocess_data(self):
        """Load and preprocess solar energy data"""
        print("Loading data...")
        df = pd.read_csv(self.data_path)

        # Remove datetime and unnecessary columns
        if 'datetime' in df.columns:
            df = df.drop('datetime', axis=1)

        # Remove redundant features
        cols_to_drop = ['ghi', 'dhi', 'global_s', 'global_u', 'wind_speed_peak',
                        'wind_speed_std', 'CR1000', 'rsr_battery']
        for col in cols_to_drop:
            if col in df.columns:
                df = df.drop(col, axis=1)

        # Handle negative values (measurement errors)
        for col in df.columns:
            df.loc[df[col] < 0, col] = 0

        # Split features and target
        y = df.pop('dni')  # Direct Normal Irradiance
        X = df

        print(f"Dataset shape: X={X.shape}, y={y.shape}")
        return X, y

    def create_model(self, model_type, units_layer1, units_layer2, activation, learning_rate, input_shape):
        """Create a neural network model with specified hyperparameters"""
        if model_type == 'LSTM':
            model = keras.Sequential([
                keras.layers.LSTM(units_layer1, activation=activation,
                                 return_sequences=True, input_shape=(input_shape, 1)),
                keras.layers.LSTM(units_layer2, activation=activation),
                keras.layers.Dense(1)
            ])
        elif model_type == 'GRU':
            model = keras.Sequential([
                keras.layers.GRU(units_layer1, activation=activation,
                                return_sequences=True, input_shape=(input_shape, 1)),
                keras.layers.GRU(units_layer2, activation=activation),
                keras.layers.Dense(1)
            ])
        elif model_type == 'SimpleRNN':
            model = keras.Sequential([
                keras.layers.SimpleRNN(units_layer1, activation=activation,
                                      return_sequences=True, input_shape=(input_shape, 1)),
                keras.layers.SimpleRNN(units_layer2, activation=activation),
                keras.layers.Dense(1)
            ])
        else:
            raise ValueError(f"Unknown model type: {model_type}")

        optimizer = keras.optimizers.Adam(learning_rate=learning_rate)
        model.compile(loss='mae', optimizer=optimizer, metrics=['mae', 'mse'])

        return model

    def run_experiment(self, config):
        """Run a single experiment with given configuration"""
        print(f"\n{'='*60}")
        print(f"Running experiment: {config['name']}")
        print(f"{'='*60}")

        # Load data
        X, y = self.load_and_preprocess_data()

        # Split data
        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=config['test_size'], random_state=42
        )

        # Scale features
        scaler = StandardScaler()
        X_train_scaled = scaler.fit_transform(X_train)
        X_test_scaled = scaler.transform(X_test)

        # Create model
        tf.random.set_seed(42)
        model = self.create_model(
            model_type=config['model_type'],
            units_layer1=config['units_layer1'],
            units_layer2=config['units_layer2'],
            activation=config['activation'],
            learning_rate=config['learning_rate'],
            input_shape=X_train_scaled.shape[1]
        )

        print(f"Model: {config['model_type']}")
        print(f"Architecture: [{config['units_layer1']}, {config['units_layer2']}]")
        print(f"Activation: {config['activation']}")
        print(f"Learning rate: {config['learning_rate']}")
        print(f"Epochs: {config['epochs']}")
        print(f"Batch size: {config['batch_size']}")

        # Train model
        start_time = time.time()
        history = model.fit(
            tf.expand_dims(X_train_scaled, axis=-1),
            y_train,
            epochs=config['epochs'],
            batch_size=config['batch_size'],
            validation_split=0.1,
            verbose=0
        )
        training_time = time.time() - start_time

        # Evaluate model
        y_pred = model.predict(tf.expand_dims(X_test_scaled, axis=-1), verbose=0).flatten()

        mae = mean_absolute_error(y_test, y_pred)
        mse = mean_squared_error(y_test, y_pred)
        rmse = np.sqrt(mse)
        r2 = r2_score(y_test, y_pred)

        # Get final training metrics
        final_train_loss = history.history['loss'][-1]
        final_val_loss = history.history['val_loss'][-1]

        # Store results
        result = {
            'experiment_id': self.experiment_id,
            'name': config['name'],
            'model_type': config['model_type'],
            'units_layer1': config['units_layer1'],
            'units_layer2': config['units_layer2'],
            'activation': config['activation'],
            'learning_rate': config['learning_rate'],
            'epochs': config['epochs'],
            'batch_size': config['batch_size'],
            'test_size': config['test_size'],
            'mae': float(mae),
            'mse': float(mse),
            'rmse': float(rmse),
            'r2': float(r2),
            'final_train_loss': float(final_train_loss),
            'final_val_loss': float(final_val_loss),
            'training_time_seconds': float(training_time),
            'total_params': model.count_params()
        }

        self.results.append(result)

        print(f"\nResults:")
        print(f"  MAE: {mae:.4f}")
        print(f"  RMSE: {rmse:.4f}")
        print(f"  R²: {r2:.4f}")
        print(f"  Training time: {training_time:.2f}s")

        return result

    def run_all_experiments(self, experiments):
        """Run all experiments and save results"""
        print(f"\nStarting hyperparameter experiments...")
        print(f"Total experiments to run: {len(experiments)}")

        for i, config in enumerate(experiments, 1):
            print(f"\n[{i}/{len(experiments)}]")
            try:
                self.run_experiment(config)
            except Exception as e:
                print(f"ERROR in experiment '{config['name']}': {e}")
                continue

        # Save results
        self.save_results()

        return self.results

    def save_results(self):
        """Save experiment results to CSV and JSON"""
        if not self.results:
            print("No results to save")
            return

        # Save as CSV
        df = pd.DataFrame(self.results)
        csv_path = f"{self.output_dir}/hyperparameter_results_{self.experiment_id}.csv"
        df.to_csv(csv_path, index=False)
        print(f"\nResults saved to: {csv_path}")

        # Save as JSON for detailed analysis
        json_path = f"{self.output_dir}/hyperparameter_results_{self.experiment_id}.json"
        with open(json_path, 'w') as f:
            json.dump(self.results, f, indent=2)
        print(f"Results saved to: {json_path}")

        # Print summary
        self.print_summary()

    def print_summary(self):
        """Print summary of all experiments"""
        if not self.results:
            return

        df = pd.DataFrame(self.results)

        print(f"\n{'='*60}")
        print("EXPERIMENT SUMMARY")
        print(f"{'='*60}")

        print(f"\nBest models by metric:")
        print(f"  Lowest MAE: {df.loc[df['mae'].idxmin(), 'name']} (MAE={df['mae'].min():.4f})")
        print(f"  Lowest RMSE: {df.loc[df['rmse'].idxmin(), 'name']} (RMSE={df['rmse'].min():.4f})")
        print(f"  Highest R²: {df.loc[df['r2'].idxmax(), 'name']} (R²={df['r2'].max():.4f})")
        print(f"  Fastest training: {df.loc[df['training_time_seconds'].idxmin(), 'name']} ({df['training_time_seconds'].min():.2f}s)")

        print(f"\nModel type comparison (average MAE):")
        for model_type in df['model_type'].unique():
            avg_mae = df[df['model_type'] == model_type]['mae'].mean()
            print(f"  {model_type}: {avg_mae:.4f}")


def get_experiment_configurations():
    """Define all hyperparameter configurations to test"""
    experiments = [
        # Baseline LSTM experiments
        {
            'name': 'LSTM_Baseline',
            'model_type': 'LSTM',
            'units_layer1': 128,
            'units_layer2': 64,
            'activation': 'relu',
            'learning_rate': 0.001,
            'epochs': 50,
            'batch_size': 32,
            'test_size': 0.2
        },
        {
            'name': 'LSTM_Large',
            'model_type': 'LSTM',
            'units_layer1': 300,
            'units_layer2': 200,
            'activation': 'relu',
            'learning_rate': 0.01,
            'epochs': 50,
            'batch_size': 32,
            'test_size': 0.2
        },
        {
            'name': 'LSTM_Small',
            'model_type': 'LSTM',
            'units_layer1': 64,
            'units_layer2': 32,
            'activation': 'relu',
            'learning_rate': 0.001,
            'epochs': 50,
            'batch_size': 32,
            'test_size': 0.2
        },

        # GRU experiments
        {
            'name': 'GRU_Baseline',
            'model_type': 'GRU',
            'units_layer1': 128,
            'units_layer2': 64,
            'activation': 'relu',
            'learning_rate': 0.001,
            'epochs': 50,
            'batch_size': 32,
            'test_size': 0.2
        },
        {
            'name': 'GRU_Large',
            'model_type': 'GRU',
            'units_layer1': 300,
            'units_layer2': 200,
            'activation': 'relu',
            'learning_rate': 0.001,
            'epochs': 50,
            'batch_size': 32,
            'test_size': 0.2
        },

        # Learning rate variations
        {
            'name': 'LSTM_LR_High',
            'model_type': 'LSTM',
            'units_layer1': 128,
            'units_layer2': 64,
            'activation': 'relu',
            'learning_rate': 0.01,
            'epochs': 50,
            'batch_size': 32,
            'test_size': 0.2
        },
        {
            'name': 'LSTM_LR_Low',
            'model_type': 'LSTM',
            'units_layer1': 128,
            'units_layer2': 64,
            'activation': 'relu',
            'learning_rate': 0.0001,
            'epochs': 50,
            'batch_size': 32,
            'test_size': 0.2
        },

        # Activation function variations
        {
            'name': 'LSTM_Tanh',
            'model_type': 'LSTM',
            'units_layer1': 128,
            'units_layer2': 64,
            'activation': 'tanh',
            'learning_rate': 0.001,
            'epochs': 50,
            'batch_size': 32,
            'test_size': 0.2
        },

        # Batch size variations
        {
            'name': 'LSTM_Batch_64',
            'model_type': 'LSTM',
            'units_layer1': 128,
            'units_layer2': 64,
            'activation': 'relu',
            'learning_rate': 0.001,
            'epochs': 50,
            'batch_size': 64,
            'test_size': 0.2
        },
        {
            'name': 'LSTM_Batch_16',
            'model_type': 'LSTM',
            'units_layer1': 128,
            'units_layer2': 64,
            'activation': 'relu',
            'learning_rate': 0.001,
            'epochs': 50,
            'batch_size': 16,
            'test_size': 0.2
        },

        # More epochs
        {
            'name': 'LSTM_Epochs_100',
            'model_type': 'LSTM',
            'units_layer1': 128,
            'units_layer2': 64,
            'activation': 'relu',
            'learning_rate': 0.001,
            'epochs': 100,
            'batch_size': 32,
            'test_size': 0.2
        },

        # SimpleRNN baseline
        {
            'name': 'SimpleRNN_Baseline',
            'model_type': 'SimpleRNN',
            'units_layer1': 128,
            'units_layer2': 64,
            'activation': 'relu',
            'learning_rate': 0.001,
            'epochs': 50,
            'batch_size': 32,
            'test_size': 0.2
        },
    ]

    return experiments


def main():
    """Main execution function"""
    if not HAS_DEPENDENCIES:
        print("ERROR: Required dependencies not installed")
        print("Please install: tensorflow, scikit-learn, pandas, numpy")
        return

    # Data path - update this to match your actual data location
    data_path = 'solar-energy-prediction/solar-energy-prediction/full_data/processed_consolidated_data.csv'

    # Create experiment manager
    experimenter = HyperparameterExperiment(data_path)

    # Get experiment configurations
    experiments = get_experiment_configurations()

    # Run all experiments
    results = experimenter.run_all_experiments(experiments)

    print(f"\n{'='*60}")
    print("ALL EXPERIMENTS COMPLETED!")
    print(f"{'='*60}")
    print(f"Total experiments: {len(results)}")
    print(f"Results saved with experiment ID: {experimenter.experiment_id}")


if __name__ == "__main__":
    main()
